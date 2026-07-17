"""Tiện ích dùng chung: bọc VnCoreNLP word-segmenter + tách từ có kèm offset ký tự.

SPEC §11 lưu ý: PhoBERT được pretrain trên văn bản ĐÃ word-segment (ghép âm tiết
bằng dấu "_"). Bỏ qua bước này làm F1 tụt vài điểm cho intent; với NER, việc
word-segment còn ảnh hưởng ranh giới token nên càng cần làm đúng.

`segment_with_offsets` trả về danh sách token đã segment KÈM offset trong VĂN BẢN
GỐC (chưa segment) — cần thiết để quy các entity (đã gán nhãn theo offset gốc
trong *_train.jsonl / *_test_real.jsonl) sang đúng token sau khi segment.

Cách quy offset: VnCoreNLP word-segment chỉ GHÉP các "từ" (đã tách theo khoảng
trắng) liền kề thành 1 token bằng "_" — không bao giờ tách nhỏ hơn hay đổi thứ
tự một từ-khoảng-trắng gốc. Nên: tách văn bản gốc theo khoảng trắng để có danh
sách (word, start, end); với mỗi token đã segment, nó gồm N = số lượng "_" + 1
từ gốc liên tiếp — cứ tiêu thụ lần lượt N từ gốc là suy ra được (start, end)
của token đó trong văn bản gốc.
"""

import re
from pathlib import Path

_SEGMENTER = None


_BASE_URL = "https://raw.githubusercontent.com/vncorenlp/VnCoreNLP/master"


def _download_wseg_only(save_dir: Path):
    """Tải tối thiểu cho annotator "wseg" (jar + wordsegmenter) bằng urllib —
    `py_vncorenlp.download_model()` gốc gọi `wget` qua os.system(), không có sẵn
    trên Windows, và còn tải cả model dep/ner/postagger không cần thiết cho wseg."""
    import urllib.request

    for sub in ("dep", "ner", "postagger", "wordsegmenter"):
        (save_dir / "models" / sub).mkdir(parents=True, exist_ok=True)

    files = [
        ("VnCoreNLP-1.2.jar", save_dir / "VnCoreNLP-1.2.jar"),
        ("models/wordsegmenter/vi-vocab", save_dir / "models/wordsegmenter/vi-vocab"),
        ("models/wordsegmenter/wordsegmenter.rdr", save_dir / "models/wordsegmenter/wordsegmenter.rdr"),
    ]
    for rel_url, dest in files:
        if dest.exists():
            continue
        print(f"Downloading {rel_url} ...")
        urllib.request.urlretrieve(f"{_BASE_URL}/{rel_url}", str(dest))


def get_segmenter(save_dir: Path):
    """Trả về VnCoreNLP segmenter đã sẵn sàng (tải model 1 lần nếu chưa có).

    LƯU Ý: `py_vncorenlp.VnCoreNLP.__init__` tự `os.chdir(save_dir)` và KHÔNG
    trả lại thư mục làm việc ban đầu — nếu không khôi phục, mọi đường dẫn
    tương đối gọi SAU lệnh này (vd. `--output-dir out-intent`) sẽ bị lệch vào
    trong `save_dir`. Ta khôi phục cwd ngay sau khi khởi tạo xong.
    """
    global _SEGMENTER
    if _SEGMENTER is not None:
        return _SEGMENTER

    import os

    import py_vncorenlp

    save_dir = Path(save_dir)
    save_dir.mkdir(parents=True, exist_ok=True)
    jar = save_dir / "VnCoreNLP-1.2.jar"
    if not jar.exists():
        _download_wseg_only(save_dir)

    cwd = os.getcwd()
    try:
        _SEGMENTER = py_vncorenlp.VnCoreNLP(save_dir=str(save_dir), annotators=["wseg"])
    finally:
        os.chdir(cwd)
    return _SEGMENTER


def segment_plain(segmenter, text: str) -> str:
    """Trả câu đã word-segment, ghép các câu con lại (dùng cho intent — không cần offset)."""
    sentences = segmenter.word_segment(text)
    return " ".join(sentences)


def segment_with_offsets(segmenter, text: str):
    """Trả list[(token, start, end)] — offset tính trên `text` GỐC (chưa segment)."""
    raw_words = [(m.group(0), m.start(), m.end()) for m in re.finditer(r"\S+", text)]

    sentences = segmenter.word_segment(text)
    seg_tokens = []
    for sent in sentences:
        seg_tokens.extend(sent.split(" "))

    out = []
    cursor = 0
    for tok in seg_tokens:
        if not tok:
            continue
        n = tok.count("_") + 1
        chunk = raw_words[cursor: cursor + n]
        if not chunk:
            # Lệch bất thường (không nên xảy ra) — bỏ qua token này thay vì crash.
            continue
        start = chunk[0][1]
        end = chunk[-1][2]
        out.append((tok, start, end))
        cursor += n
    return out
