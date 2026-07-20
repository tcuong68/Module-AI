"""NLU service (FastAPI) — SPEC §11 bước 2.3.

Bọc 2 model PhoBERT đã train ở GĐ2 (`ml/out-intent`, `ml/out-ner`) thành HTTP
service cho Spring Boot gọi qua `PhoBertNluServiceImpl` (bước 2.4). Hợp đồng:

    POST /nlu  {"text": "tìm phòng tầm 2tr ở cầu giấy"}
    → {"intent": "search_room", "confidence": 0.99,
       "entities": [{"label": "PRICE_MAX", "text": "2tr", "start": 14, "end": 17,
                     "score": 0.98}, ...]}

Entity trả về là SPAN THÔ theo offset ký tự trên văn bản gốc — KHÔNG chuẩn hóa
giá trị ở đây. Việc quy "2tr" → 2_000_000, "cầu giấy" → "Cầu Giấy"... thuộc tầng
`EntityNormalizer` phía Java (§3.3) — giữ 1 nơi duy nhất biết luật chuẩn hóa,
service này chỉ làm đúng phần mô hình.

Suy luận phải LẶP LẠI đúng cách encode lúc train:
  - Intent (`train_intent.py`): word-segment cả câu → tokenizer(max_length=64).
  - NER (`train_ner.py`): word-segment kèm offset gốc → encode TỪNG TỪ (PhoBERT
    không có fast tokenizer), nhãn nằm ở subword ĐẦU của mỗi từ → BIO → gộp span.

Chạy:
    uvicorn app:app --host 0.0.0.0 --port 8000
Biến môi trường (mặc định chạy được ngay từ repo):
    NLU_ML_DIR         (mặc định ../ml)   — nơi có vncorenlp_util.py
    NLU_INTENT_MODEL   (mặc định $NLU_ML_DIR/out-intent)
    NLU_NER_MODEL      (mặc định $NLU_ML_DIR/out-ner)
    VNCORENLP_DIR      — xem vncorenlp_util._resolve_save_dir (đường dẫn có dấu cách)
"""

import os
import sys
from contextlib import asynccontextmanager
from pathlib import Path

import torch
from fastapi import FastAPI
from pydantic import BaseModel

ROOT = Path(__file__).parent
ML_DIR = Path(os.environ.get("NLU_ML_DIR", ROOT.parent / "ml"))
INTENT_DIR = os.environ.get("NLU_INTENT_MODEL", str(ML_DIR / "out-intent"))
NER_DIR = os.environ.get("NLU_NER_MODEL", str(ML_DIR / "out-ner"))
# Embedding cho semantic rerank (GĐ3, SPEC §12.1) — không cần word-segment
# (SBERT tự tokenize theo BPE riêng, không phải PhoBERT).
EMBED_MODEL = os.environ.get("NLU_EMBED_MODEL", "keepitreal/vietnamese-sbert")

sys.path.insert(0, str(ML_DIR))
from vncorenlp_util import get_segmenter, segment_plain, segment_with_offsets  # noqa: E402

MAX_LENGTH = 64  # khớp train_intent.py / train_ner.py

_M = {}  # segmenter + models, nạp 1 lần lúc startup


@asynccontextmanager
async def lifespan(app: FastAPI):
    from sentence_transformers import SentenceTransformer
    from transformers import (
        AutoModelForSequenceClassification,
        AutoModelForTokenClassification,
        AutoTokenizer,
    )

    torch.set_num_threads(max(1, (os.cpu_count() or 4) // 2))
    _M["segmenter"] = get_segmenter(ML_DIR / "vncorenlp")
    _M["intent_tok"] = AutoTokenizer.from_pretrained(INTENT_DIR)
    _M["intent_model"] = AutoModelForSequenceClassification.from_pretrained(INTENT_DIR).eval()
    _M["ner_tok"] = AutoTokenizer.from_pretrained(NER_DIR)
    _M["ner_model"] = AutoModelForTokenClassification.from_pretrained(NER_DIR).eval()
    _M["embed_model"] = SentenceTransformer(EMBED_MODEL)
    yield
    _M.clear()


app = FastAPI(title="RoomFinder NLU (PhoBERT)", lifespan=lifespan)


class NluRequest(BaseModel):
    text: str


class EntitySpan(BaseModel):
    label: str
    text: str
    start: int
    end: int
    score: float


class NluResponse(BaseModel):
    intent: str
    confidence: float
    entities: list[EntitySpan]


class EmbedRequest(BaseModel):
    text: str


class EmbedResponse(BaseModel):
    vector: list[float]


@app.get("/health")
def health():
    return {"status": "ok", "intent_model": INTENT_DIR, "ner_model": NER_DIR, "embed_model": EMBED_MODEL}


@app.post("/embed", response_model=EmbedResponse)
def embed(req: EmbedRequest) -> EmbedResponse:
    """Embedding cho semantic rerank (GĐ3, SPEC §12.1). Dùng cho cả mô tả phòng
    (tính 1 lần, cache ở backend) lẫn câu hỏi người dùng (tính mỗi lượt cần rerank)."""
    vec = _M["embed_model"].encode(req.text.strip() or " ", normalize_embeddings=True)
    return EmbedResponse(vector=vec.tolist())


@app.post("/nlu", response_model=NluResponse)
def nlu(req: NluRequest) -> NluResponse:
    text = req.text.strip()
    if not text:
        return NluResponse(intent="out_of_scope", confidence=1.0, entities=[])
    intent, confidence = classify_intent(text)
    entities = extract_entities(text)
    return NluResponse(intent=intent, confidence=confidence, entities=entities)


def classify_intent(text: str) -> tuple[str, float]:
    seg = segment_plain(_M["segmenter"], text)
    enc = _M["intent_tok"](seg, truncation=True, max_length=MAX_LENGTH, return_tensors="pt")
    with torch.no_grad():
        logits = _M["intent_model"](**enc).logits[0]
    probs = torch.softmax(logits, dim=-1)
    idx = int(probs.argmax())
    return _M["intent_model"].config.id2label[idx], float(probs[idx])


def extract_entities(text: str) -> list[EntitySpan]:
    seg = segment_with_offsets(_M["segmenter"], text)
    if not seg:
        return []

    tok = _M["ner_tok"]
    model = _M["ner_model"]

    # Encode từng từ đã segment, nhớ vị trí subword đầu của mỗi từ (như lúc train).
    input_ids = [tok.bos_token_id]
    first_subword_pos = []  # song song với seg (từ bị cắt bởi budget → bỏ)
    budget = MAX_LENGTH - 1  # chừa chỗ cho </s>
    for word, _, _ in seg:
        piece_ids = tok.encode(word, add_special_tokens=False)
        if not piece_ids or len(input_ids) + len(piece_ids) > budget:
            first_subword_pos.append(None)
            continue
        first_subword_pos.append(len(input_ids))
        input_ids.extend(piece_ids)
    input_ids.append(tok.eos_token_id)

    enc = {
        "input_ids": torch.tensor([input_ids]),
        "attention_mask": torch.ones(1, len(input_ids), dtype=torch.long),
    }
    with torch.no_grad():
        logits = model(**enc).logits[0]
    probs = torch.softmax(logits, dim=-1)

    id2label = model.config.id2label
    word_labels = []  # [(label, prob)] song song với seg
    for pos in first_subword_pos:
        if pos is None:
            word_labels.append(("O", 1.0))
        else:
            idx = int(probs[pos].argmax())
            word_labels.append((id2label[idx], float(probs[pos][idx])))

    return decode_bio(text, seg, word_labels)


def decode_bio(text: str, seg, word_labels) -> list[EntitySpan]:
    """Gộp nhãn BIO theo từ thành span trên văn bản gốc. Chấp nhận I-X mở đầu
    entity (model đôi khi bỏ lỡ B-) — quan trọng recall hơn đúng chuẩn BIO."""
    spans = []
    cur = None  # {"label","start","end","scores"}

    def flush():
        nonlocal cur
        if cur is not None:
            spans.append(EntitySpan(
                label=cur["label"],
                text=text[cur["start"]:cur["end"]],
                start=cur["start"],
                end=cur["end"],
                score=sum(cur["scores"]) / len(cur["scores"]),
            ))
            cur = None

    for (word, start, end), (label, prob) in zip(seg, word_labels):
        if label == "O":
            flush()
            continue
        prefix, ent_type = label.split("-", 1)
        if prefix == "I" and cur is not None and cur["label"] == ent_type:
            cur["end"] = end
            cur["scores"].append(prob)
        else:
            flush()
            cur = {"label": ent_type, "start": start, "end": end, "scores": [prob]}
    flush()
    return spans
