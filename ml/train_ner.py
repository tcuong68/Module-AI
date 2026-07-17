"""Huấn luyện PhoBERT NER (BIO) — SPEC §11 bước 2.2.

Dữ liệu nguồn (`data/*.jsonl`) lưu entity theo OFFSET KÝ TỰ trên văn bản gốc
(xem `generate_dataset.py`). Script này:
  1. Word-segment bằng VnCoreNLP, quy offset token-đã-segment về offset gốc
     (`vncorenlp_util.segment_with_offsets`).
  2. Gán nhãn BIO cho từng token đã segment dựa trên overlap với entity span.
  3. Tokenize bằng PhoBERT fast tokenizer với `is_split_into_words=True`, dùng
     `word_ids()` để lan nhãn sang subword đầu tiên của mỗi từ, `-100` cho các
     subword còn lại (và token đặc biệt) — công thức chuẩn của HF token
     classification.

Chạy:
    python train_ner.py
    python train_ner.py --max-train 100 --epochs 1 --output-dir out-ner-smoke
"""

import argparse
import json
from pathlib import Path

import evaluate
import numpy as np
from datasets import Dataset
from transformers import (
    AutoModelForTokenClassification,
    AutoTokenizer,
    DataCollatorForTokenClassification,
    Trainer,
    TrainingArguments,
)

from vncorenlp_util import get_segmenter, segment_with_offsets

ROOT = Path(__file__).parent
DATA_DIR = ROOT / "data"
VNCORENLP_DIR = ROOT / "vncorenlp"
MODEL_NAME = "vinai/phobert-base-v2"

ENTITY_LABELS = [
    "PRICE_MAX", "PRICE_MIN", "LOCATION", "POI", "RADIUS", "AREA_MIN",
    "UTILITY", "ROOM_TYPE", "DATETIME", "ROOM_REF",
]
BIO_LABELS = ["O"] + [f"{p}-{l}" for l in ENTITY_LABELS for p in ("B", "I")]
LABEL2ID = {l: i for i, l in enumerate(BIO_LABELS)}
ID2LABEL = {i: l for i, l in enumerate(BIO_LABELS)}


def load_jsonl(path: Path):
    with path.open(encoding="utf-8") as f:
        return [json.loads(line) for line in f]


def assign_bio(seg_tokens, entities):
    """seg_tokens: [(token,start,end)]. entities: [{"start","end","label"}].
    Trả list nhãn BIO song song với seg_tokens."""
    tags = ["O"] * len(seg_tokens)
    for ent in entities:
        first = True
        for i, (_, tstart, tend) in enumerate(seg_tokens):
            if tstart < ent["end"] and tend > ent["start"]:  # overlap
                tags[i] = ("B-" if first else "I-") + ent["label"]
                first = False
    return tags


def to_dataset(segmenter, rows):
    all_tokens, all_tags = [], []
    for r in rows:
        seg = segment_with_offsets(segmenter, r["text"])
        if not seg:
            continue
        tokens = [t for t, _, _ in seg]
        tags = assign_bio(seg, r["entities"])
        all_tokens.append(tokens)
        all_tags.append([LABEL2ID[t] for t in tags])
    return Dataset.from_dict({"tokens": all_tokens, "tags": all_tags})


def make_tokenize_fn(tokenizer, max_length=64):
    """PhoBERT chỉ có tokenizer "slow" (không có bản Fast) nên không dùng được
    `word_ids()`. Thay vào đó: encode TỪNG TỪ (đã word-segment) riêng lẻ, gán
    nhãn cho subword ĐẦU TIÊN của mỗi từ, `-100` cho các subword còn lại — cách
    làm chuẩn khi không có fast tokenizer."""

    bos_id = tokenizer.bos_token_id
    eos_id = tokenizer.eos_token_id

    def fn(batch):
        all_input_ids, all_labels = [], []
        for tokens, tags in zip(batch["tokens"], batch["tags"]):
            input_ids, labels = [], []
            if bos_id is not None:
                input_ids.append(bos_id)
                labels.append(-100)
            for word, tag in zip(tokens, tags):
                piece_ids = tokenizer.encode(word, add_special_tokens=False)
                if not piece_ids:
                    continue
                input_ids.extend(piece_ids)
                labels.extend([tag] + [-100] * (len(piece_ids) - 1))
            budget = max_length - (1 if eos_id is not None else 0)
            input_ids = input_ids[:budget]
            labels = labels[:budget]
            if eos_id is not None:
                input_ids.append(eos_id)
                labels.append(-100)
            all_input_ids.append(input_ids)
            all_labels.append(labels)
        return {
            "input_ids": all_input_ids,
            "attention_mask": [[1] * len(ids) for ids in all_input_ids],
            "labels": all_labels,
        }

    return fn


def make_training_args(output_dir, num_epochs, batch_size):
    common = dict(
        output_dir=output_dir,
        num_train_epochs=num_epochs,
        per_device_train_batch_size=batch_size,
        per_device_eval_batch_size=batch_size * 2,
        learning_rate=3e-5,
        save_strategy="epoch",
        load_best_model_at_end=True,
        metric_for_best_model="f1",
        logging_steps=20,
        report_to=[],
    )
    try:
        return TrainingArguments(eval_strategy="epoch", **common)
    except TypeError:
        return TrainingArguments(evaluation_strategy="epoch", **common)


def main(max_train, num_epochs, output_dir, batch_size):
    segmenter = get_segmenter(VNCORENLP_DIR)

    train_rows = load_jsonl(DATA_DIR / "ner_train.jsonl")
    test_rows = load_jsonl(DATA_DIR / "ner_test_real.jsonl")
    if max_train:
        train_rows = train_rows[:max_train]

    train_ds = to_dataset(segmenter, train_rows)
    test_ds = to_dataset(segmenter, test_rows)

    tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
    model = AutoModelForTokenClassification.from_pretrained(
        MODEL_NAME, num_labels=len(BIO_LABELS), id2label=ID2LABEL, label2id=LABEL2ID
    )

    tokenize_fn = make_tokenize_fn(tokenizer)
    train_ds = train_ds.map(tokenize_fn, batched=True)
    test_ds = test_ds.map(tokenize_fn, batched=True)

    seqeval = evaluate.load("seqeval")

    def compute_metrics(pred):
        preds = np.argmax(pred.predictions, axis=2)
        true_labels, true_preds = [], []
        for pred_row, label_row in zip(preds, pred.label_ids):
            cur_labels, cur_preds = [], []
            for p, l in zip(pred_row, label_row):
                if l == -100:
                    continue
                cur_labels.append(ID2LABEL[l])
                cur_preds.append(ID2LABEL[p])
            true_labels.append(cur_labels)
            true_preds.append(cur_preds)
        result = seqeval.compute(predictions=true_preds, references=true_labels)
        return {
            "precision": result["overall_precision"],
            "recall": result["overall_recall"],
            "f1": result["overall_f1"],
            "accuracy": result["overall_accuracy"],
        }

    trainer = Trainer(
        model=model,
        args=make_training_args(output_dir, num_epochs, batch_size),
        train_dataset=train_ds,
        eval_dataset=test_ds,
        data_collator=DataCollatorForTokenClassification(tokenizer),
        compute_metrics=compute_metrics,
    )
    trainer.train()
    metrics = trainer.evaluate()
    print("=== Ket qua tren ner_test_real.jsonl (PROXY, xem canh bao trong build_proxy_testset.py) ===")
    print(metrics)
    trainer.save_model(output_dir)
    tokenizer.save_pretrained(output_dir)
    return metrics


if __name__ == "__main__":
    import io
    import sys

    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

    parser = argparse.ArgumentParser()
    parser.add_argument("--max-train", type=int, default=None, help="Gioi han so cau train (dung cho smoke test)")
    parser.add_argument("--epochs", type=int, default=8)
    parser.add_argument("--batch-size", type=int, default=16)
    parser.add_argument("--output-dir", default=str(ROOT / "out-ner"))
    args = parser.parse_args()
    main(args.max_train, args.epochs, args.output_dir, args.batch_size)
