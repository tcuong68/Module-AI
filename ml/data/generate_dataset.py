"""Sinh dataset synthetic cho GĐ2 (§11, §13 SPEC): intent_train.jsonl + ner_train.jsonl.

Cách hoạt động: mỗi câu được dựng từ các "mảnh" (piece) — mảnh literal (text thường)
hoặc mảnh entity (text + nhãn). Vì ta tự ghép chuỗi nên biết CHÍNH XÁC offset ký tự
của từng entity ngay khi sinh — không cần gán nhãn tay.

Định dạng xuất:
  intent_train.jsonl: {"text": str, "intent": str}
  ner_train.jsonl:    {"text": str, "entities": [{"start": int, "end": int, "label": str}]}

`entities` dùng offset KÝ TỰ (giống Doccano/spaCy), không phải BIO theo token — việc
word-segment (VnCoreNLP) + quy đổi sang BIO theo token được làm ở train_ner.py, vì
ranh giới token phụ thuộc bộ segmenter, không nên cố định cứng lúc sinh dữ liệu.

Nhiễu (~15% mẫu, §13.2): bỏ dấu toàn câu (unicodedata NFD + lọc combining mark).
Đây là phép biến đổi 1-đối-1 ký tự nên offset entity KHÔNG đổi — an toàn để áp dụng
sau khi đã tính xong offset.
"""

import json
import random
import unicodedata
from pathlib import Path

random.seed(42)

OUT_DIR = Path(__file__).parent

# ---------------------------------------------------------------------------
# Vocab — khớp với SYNONYMS/DISTRICTS thật trong backend (UtilityNormalizer,
# LocationNormalizer) để dữ liệu sinh ra phản ánh đúng hệ thống.
# ---------------------------------------------------------------------------

PRICE_PHRASES = [
    "2 triệu", "2tr5", "3 triệu", "3tr5", "3 củ", "4 triệu", "1 triệu rưỡi",
    "500k", "800k", "1tr2", "2 triệu rưỡi", "3 triệu rưỡi", "5 triệu", "6tr",
    "4tr5", "1tr8", "2tr8",
]

LOCATIONS = [
    "Thanh Xuân", "Cầu Giấy", "Hà Đông", "Đống Đa", "Hai Bà Trưng",
    "Ba Đình", "Hoàng Mai", "Nam Từ Liêm", "Bắc Từ Liêm", "Long Biên",
    "Tây Hồ", "Hoàn Kiếm",
]

POIS = [
    "PTIT", "Bách Khoa", "Đại học Quốc gia", "bến xe Mỹ Đình",
    "Học viện Bưu chính Viễn thông", "Đại học Kinh tế Quốc dân",
    "Đại học Ngoại thương", "Times City",
]

# Mỗi entry: (key chuẩn, list biến thể raw — khớp SYNONYMS trong UtilityNormalizer)
UTILITY_VARIANTS = {
    "air_conditioner": ["điều hòa", "điều hoà", "máy lạnh"],
    "parking": ["chỗ để xe", "chỗ đỗ xe", "bãi xe", "chỗ gửi xe"],
    "wifi": ["wifi", "mạng", "internet"],
    "washing_machine": ["máy giặt"],
}
UTILITY_PHRASES = [v for vs in UTILITY_VARIANTS.values() for v in vs]

AREA_PHRASES = ["18m2", "20m2", "20 m²", "22m²", "25m2", "30 m²"]

DATETIME_PHRASES = [
    "chiều mai", "sáng mai", "9h sáng thứ 3", "3h chiều mai",
    "tối nay lúc 7h", "10h sáng thứ 5", "chiều thứ 4", "2h chiều thứ 6",
    "sáng thứ 2", "8h tối mai",
]

ROOM_REF_PHRASES = [
    "phòng số 1", "phòng số 2", "phòng số 3", "phòng đầu tiên",
    "phòng thứ hai", "cái đầu tiên", "phòng 1", "phòng 2", "phòng 3",
]

ROOM_TYPE_PHRASES = ["phòng trọ", "chung cư mini", "nhà nguyên căn", "phòng khép kín"]

# Hậu tố lịch sự/điền từ ngẫu nhiên — không mang entity, chỉ tăng đa dạng câu
# (tránh trùng lặp khi combinatorial space của 1 template gốc còn nhỏ).
SUFFIXES = ["", " nhé", " giúp mình", " ạ", " với", " nha", " được không"]


def suf():
    return random.choice(SUFFIXES)

INTENTS = [
    "search_room", "refine_search", "room_detail", "compare_rooms",
    "book_appointment", "calculate_cost", "policy_inquiry", "out_of_scope",
]


def strip_diacritics(s: str) -> str:
    """Bỏ dấu tiếng Việt — biến đổi 1 ký tự -> 1 ký tự nên KHÔNG làm lệch offset."""
    s = unicodedata.normalize("NFD", s)
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    s = s.replace("đ", "d").replace("Đ", "D")
    return unicodedata.normalize("NFC", s)


def render(parts):
    """parts: list of (text, label_or_None). Trả (full_text, entities[])."""
    out = []
    entities = []
    pos = 0
    for text, label in parts:
        out.append(text)
        if label is not None:
            entities.append({"start": pos, "end": pos + len(text), "label": label})
        pos += len(text)
    return "".join(out), entities


def maybe_noise(text, entities):
    if random.random() < 0.15:
        return strip_diacritics(text), entities
    return text, entities


# ---------------------------------------------------------------------------
# Template builders — mỗi hàm trả về parts cho MỘT câu (rng-dependent).
# ---------------------------------------------------------------------------

def pick_price(kind):
    """kind: 'max' hoặc 'min' — trả (parts_prefix, phrase, label)."""
    phrase = random.choice(PRICE_PHRASES)
    if kind == "max":
        prefix = random.choice(["dưới ", "tối đa ", "khoảng "])
        return prefix, phrase, "PRICE_MAX"
    prefix = random.choice(["từ ", "trên "])
    return prefix, phrase, "PRICE_MIN"


def tpl_search_room():
    loc = random.choice(LOCATIONS)
    poi = random.choice(POIS)
    price_prefix, price_phrase, price_label = pick_price("max")
    util = random.choice(UTILITY_PHRASES)
    area = random.choice(AREA_PHRASES)

    variant = random.randint(0, 6)
    if variant == 0:
        return [("Tìm phòng ", None), (price_prefix, None), (price_phrase, price_label),
                (" ở ", None), (loc, "LOCATION")]
    if variant == 1:
        return [("Có phòng nào ", None), (price_prefix, None), (price_phrase, price_label),
                (" gần ", None), (poi, "POI"), (" không", None)]
    if variant == 2:
        return [("Mình muốn thuê phòng ", None), (loc, "LOCATION"), (" khoảng ", None),
                (price_phrase, "PRICE_MAX"), (" có ", None), (util, "UTILITY")]
    if variant == 3:
        return [("Cho hỏi phòng trọ ", None), (loc, "LOCATION"), (" giá ", None),
                (price_phrase, "PRICE_MAX"), (" còn không ạ", None)]
    if variant == 4:
        return [("Kiếm giúp mình phòng ", None), (price_phrase, "PRICE_MAX"),
                (" đổ lại, ", None), (loc, "LOCATION"), (", có ", None), (util, "UTILITY")]
    if variant == 5:
        return [("Tìm phòng ", None), (price_prefix, None), (price_phrase, price_label),
                (", diện tích ", None), (area, "AREA_MIN"), (", ở ", None), (loc, "LOCATION")]
    return [("Phòng nào ", None), (price_prefix, None), (price_phrase, price_label),
            (" mà gần ", None), (poi, "POI"), (" có ", None), (util, "UTILITY"), (" không", None)]


def tpl_refine_search():
    util = random.choice(UTILITY_PHRASES)
    loc = random.choice(LOCATIONS)
    variant = random.randint(0, 6)
    if variant == 0:
        return [("Có ", None), (util, "UTILITY"), (" nữa không", None)]
    if variant == 1:
        return [("Rẻ hơn nữa đi", None)]
    if variant == 2:
        return [("Đổi sang khu vực ", None), (loc, "LOCATION"), (" được không", None)]
    if variant == 3:
        return [("Cần thêm ", None), (util, "UTILITY"), (" và ", None),
                (random.choice(UTILITY_PHRASES), "UTILITY")]
    if variant == 4:
        return [("Tìm phòng khác", None)]
    if variant == 5:
        return [("Bỏ yêu cầu ", None), (util, "UTILITY")]
    _, price_phrase, _ = pick_price("max")
    return [("Giảm giá xuống dưới ", None), (price_phrase, "PRICE_MAX"), (" thôi", None)]


def tpl_room_detail():
    ref = random.choice(ROOM_REF_PHRASES)
    variant = random.randint(0, 4)
    if variant == 0:
        return [("Cho mình xem chi tiết ", None), (ref, "ROOM_REF")]
    if variant == 1:
        return [(ref.capitalize(), "ROOM_REF"), (" có gì đặc biệt không", None)]
    if variant == 2:
        return [("Xem kỹ thông tin ", None), (ref, "ROOM_REF")]
    if variant == 3:
        return [("Chi tiết ", None), (ref, "ROOM_REF"), (" thế nào", None)]
    return [("Thông tin đầy đủ về ", None), (ref, "ROOM_REF")]


def tpl_compare_rooms():
    ref1, ref2 = random.sample(ROOM_REF_PHRASES, 2)
    variant = random.randint(0, 2)
    if variant == 0:
        return [("So sánh ", None), (ref1, "ROOM_REF"), (" và ", None), (ref2, "ROOM_REF"), (" giúp mình", None)]
    if variant == 1:
        return [("Trong ", None), (ref1, "ROOM_REF"), (" với ", None), (ref2, "ROOM_REF"),
                (" thì nên chọn cái nào tốt hơn", None)]
    return [(ref1.capitalize(), "ROOM_REF"), (" và ", None), (ref2, "ROOM_REF"), (" cái nào rẻ hơn", None)]


def tpl_book_appointment():
    ref = random.choice(ROOM_REF_PHRASES)
    dt = random.choice(DATETIME_PHRASES)
    variant = random.randint(0, 2)
    if variant == 0:
        return [("Đặt lịch xem ", None), (ref, "ROOM_REF"), (" ", None), (dt, "DATETIME")]
    if variant == 1:
        return [("Cho mình hẹn xem ", None), (ref, "ROOM_REF"), (" vào ", None), (dt, "DATETIME"), (" nhé", None)]
    return [("Mình muốn xem ", None), (ref, "ROOM_REF"), (" ", None), (dt, "DATETIME"), (" được không", None)]


def tpl_calculate_cost():
    ref = random.choice(ROOM_REF_PHRASES)
    variant = random.randint(0, 3)
    if variant == 0:
        return [("Ở ", None), (ref, "ROOM_REF"), (" thì mỗi tháng hết bao nhiêu tiền", None)]
    if variant == 1:
        return [("Tính giúp mình chi phí hàng tháng của ", None), (ref, "ROOM_REF")]
    if variant == 2:
        return [("Chi phí thuê ", None), (ref, "ROOM_REF"), (" mỗi tháng khoảng bao nhiêu", None)]
    return [(ref.capitalize(), "ROOM_REF"), (" tính đủ điện nước hết bao nhiêu tiền", None)]


POLICY_SENTENCES = [
    "Hợp đồng thuê phòng có thời hạn tối thiểu bao lâu?",
    "Đặt cọc bao nhiêu tiền là hợp lý?",
    "Quy định về việc hủy hợp đồng thế nào?",
    "Nếu muốn gia hạn hợp đồng thì làm sao?",
    "Chính sách hoàn cọc khi trả phòng sớm ra sao?",
    "Cho hỏi quy trình ký hợp đồng thuê phòng?",
]

OUT_OF_SCOPE_SENTENCES = [
    "Xin chào", "Bạn tên gì?", "Hôm nay thời tiết thế nào?",
    "Bạn có thể hát một bài không?", "Cảm ơn nhé", "Tạm biệt",
    "1 cộng 1 bằng mấy?", "Kể chuyện cười đi",
]

TEMPLATE_BY_INTENT = {
    "search_room": tpl_search_room,
    "refine_search": tpl_refine_search,
    "room_detail": tpl_room_detail,
    "compare_rooms": tpl_compare_rooms,
    "book_appointment": tpl_book_appointment,
    "calculate_cost": tpl_calculate_cost,
}

COUNTS = {
    "search_room": 500,
    "refine_search": 400,
    "room_detail": 300,
    "compare_rooms": 300,
    "book_appointment": 350,
    "calculate_cost": 250,
    "policy_inquiry": 200,
    "out_of_scope": 200,
}


def generate_intent_and_ner():
    intent_rows = []
    ner_rows = []

    for intent, builder in TEMPLATE_BY_INTENT.items():
        n = COUNTS[intent]
        seen = set()
        tries = 0
        while len([r for r in intent_rows if r["intent"] == intent]) < n and tries < n * 20:
            tries += 1
            parts = builder()
            extra = suf()
            if extra:
                parts = parts + [(extra, None)]
            text, entities = render(parts)
            text, entities = maybe_noise(text, entities)
            if text in seen:
                continue
            seen.add(text)
            intent_rows.append({"text": text, "intent": intent})
            if entities:
                ner_rows.append({"text": text, "entities": entities})

    for label, sentences, n in (
        ("policy_inquiry", POLICY_SENTENCES, COUNTS["policy_inquiry"]),
        ("out_of_scope", OUT_OF_SCOPE_SENTENCES, COUNTS["out_of_scope"]),
    ):
        for i in range(n):
            base = random.choice(sentences)
            text = base
            if random.random() < 0.15:
                text = strip_diacritics(text)
            intent_rows.append({"text": text, "intent": label})

    random.shuffle(intent_rows)
    random.shuffle(ner_rows)
    return intent_rows, ner_rows


def write_jsonl(path: Path, rows):
    with path.open("w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")


def main():
    import io
    import sys

    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

    intent_rows, ner_rows = generate_intent_and_ner()
    write_jsonl(OUT_DIR / "intent_train.jsonl", intent_rows)
    write_jsonl(OUT_DIR / "ner_train.jsonl", ner_rows)
    print(f"intent_train.jsonl: {len(intent_rows)} cau")
    print(f"ner_train.jsonl:    {len(ner_rows)} cau (co >=1 entity)")
    from collections import Counter
    print("Phan bo intent:", Counter(r["intent"] for r in intent_rows))


if __name__ == "__main__":
    main()
