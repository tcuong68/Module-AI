"""Thí nghiệm so sánh NLU — SPEC §14.4 (checkpoint GĐ2).

Chạy CÙNG MỘT test set qua 3 cài đặt NluService rồi in bảng so sánh:
    A. llm     — Gemini prompt-JSON (GĐ1, prompt copy nguyên văn LlmNluServiceImpl)
    B. phobert — nlu-service FastAPI (GĐ2, cần service chạy ở --phobert-url)
    C. fc      — Gemini function-calling (đường cơ sở "thời tiền-LLM", SPEC §14.4C)

⚠️ Test set hiện là bộ PROXY tay viết (xem cảnh báo trong build_proxy_testset.py)
— số liệu dùng để so sánh TƯƠNG ĐỐI giữa các phương án, chưa phải minh chứng
tuyệt đối khi bảo vệ (cần thay bằng dữ liệu thật §13.3).

Cách so NER công bằng: LLM trả slot đã chuẩn hóa, PhoBERT trả span thô — không
so trực tiếp được. Script quy TẤT CẢ (gold span, PhoBERT span, LLM filters) về
cùng dạng SLOT chuẩn hóa bằng cùng một bộ normalizer Python (mirror các
normalizer Java) rồi tính micro P/R/F1 trên từng (slot, giá_trị). Riêng PhoBERT
tính thêm Span-F1 (khớp đúng label+start+end) — metric "NER thuần" không có
bên LLM. DATETIME/RADIUS chỉ chấm CÓ/KHÔNG (không so giá trị — chuẩn hóa
datetime 2 bên khác nhau, so giá trị sẽ phạt oan).

Chạy:
    python eval_nlu_compare.py --side phobert
    GEMINI_API_KEY=... python eval_nlu_compare.py --side llm
    GEMINI_API_KEY=... python eval_nlu_compare.py --side fc
    python eval_nlu_compare.py --report        # in + ghi bảng từ kết quả đã lưu

Kết quả từng câu cache ở eval-results/{side}_{dataset}.jsonl — chạy lại tự
resume (bỏ qua câu đã có), xóa file để đo lại từ đầu.
"""

import argparse
import json
import os
import re
import time
import unicodedata
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).parent
DATA = ROOT / "data"
OUT = ROOT / "eval-results"

INTENTS = [
    "search_room", "refine_search", "room_detail", "compare_rooms",
    "book_appointment", "calculate_cost", "policy_inquiry", "out_of_scope",
]

# Giá Gemini flash-lite (USD / 1M token) — CẬP NHẬT theo bảng giá hiện hành
# trước khi trích dẫn vào báo cáo. Free tier: chi phí tiền mặt = 0 trong quota.
PRICE_IN_PER_M = 0.10
PRICE_OUT_PER_M = 0.40
USD_VND = 26_000

# Prompt A — copy NGUYÊN VĂN từ LlmNluServiceImpl.SYSTEM để so đúng cái đang chạy.
SYSTEM_PROMPT = """Bạn là bộ phân tích ngôn ngữ cho hệ thống tìm phòng trọ.
Với câu tiếng Việt của người dùng, hãy trả về DUY NHẤT một object JSON,
KHÔNG markdown, KHÔNG giải thích, KHÔNG code fence.
Schema:
{"intent": <một trong: search_room|refine_search|room_detail|
            compare_rooms|book_appointment|calculate_cost|
            policy_inquiry|out_of_scope>,
 "confidence": <số 0..1>,
 "entities": {"price_min":null,"price_max":null,"location":null,
              "poi":null,"radius_m":null,"area_min":null,
              "utilities":[],"room_type":null,"datetime":null,
              "room_refs":[]}}
Quy ước:
- null nghĩa là người dùng KHÔNG nhắc đến (khác với false = "không cần").
- Giá quy đổi ra số nguyên VND: "3 triệu"->3000000, "3tr5"->3500000, "500k"->500000.
- utilities dùng key chuẩn: air_conditioner, parking, wifi, washing_machine.
- "rẻ hơn nữa", "gần hơn", "có ... nữa" là refine_search (không phải search_room mới).
- location là tên quận; poi là địa điểm mốc như "PTIT","Bách Khoa".

Ví dụ:
User: "Tìm phòng dưới 3 triệu ở Thanh Xuân có điều hòa"
{"intent":"search_room","confidence":0.96,"entities":{"price_min":null,
"price_max":3000000,"location":"Thanh Xuân","poi":null,"radius_m":null,
"area_min":null,"utilities":["air_conditioner"],"room_type":null,
"datetime":null,"room_refs":[]}}
User: "Có phòng nào gần PTIT không"
{"intent":"search_room","confidence":0.93,"entities":{"price_min":null,
"price_max":null,"location":null,"poi":"PTIT","radius_m":null,
"area_min":null,"utilities":[],"room_type":null,"datetime":null,"room_refs":[]}}
User: "Rẻ hơn nữa đi"
{"intent":"refine_search","confidence":0.9,"entities":{"price_min":null,
"price_max":null,"location":null,"poi":null,"radius_m":null,"area_min":null,
"utilities":[],"room_type":null,"datetime":null,"room_refs":[]}}
"""

# Schema C — cùng slot với A nhưng dạng function declaration (SPEC §14.4C).
FC_TOOL = {
    "function_declarations": [{
        "name": "nlu_result",
        "description": "Ket qua phan tich cau tieng Viet cua nguoi dung tim phong tro",
        "parameters": {
            "type": "OBJECT",
            "properties": {
                "intent": {"type": "STRING", "enum": INTENTS},
                "price_min": {"type": "INTEGER", "description": "VND, null neu khong nhac"},
                "price_max": {"type": "INTEGER"},
                "location": {"type": "STRING", "description": "ten quan"},
                "poi": {"type": "STRING", "description": "dia diem moc: PTIT, Bach Khoa..."},
                "radius_m": {"type": "INTEGER"},
                "area_min": {"type": "NUMBER"},
                "utilities": {"type": "ARRAY", "items": {"type": "STRING",
                    "enum": ["air_conditioner", "parking", "wifi", "washing_machine"]}},
                "room_type": {"type": "STRING",
                    "enum": ["PHONG_TRO", "CHUNG_CU_MINI", "NHA_NGUYEN_CAN"]},
                "datetime": {"type": "STRING"},
                "room_refs": {"type": "ARRAY", "items": {"type": "INTEGER"}},
            },
            "required": ["intent"],
        },
    }]
}


# --- Mini-normalizer (mirror các normalizer Java — Price/Utility/RoomRef...) ---

def strip_accent(s: str) -> str:
    n = unicodedata.normalize("NFD", s.lower())
    return "".join(c for c in n if not unicodedata.combining(c)).replace("đ", "d")


def norm_price(span) -> int | None:
    if span is None:
        return None
    if isinstance(span, (int, float)):
        return int(span)
    s = re.sub(r"[,.]", "", str(span).lower())
    m = re.search(r"(\d+)\s*(triệu|tr|củ|chai)\s*(\d+)?", s)
    if m:
        v = int(m.group(1)) * 1_000_000
        if m.group(3):
            v += int(m.group(3)) * 100_000
        return v
    m = re.search(r"(\d+)\s*(nghìn|ngàn|k)", s)
    if m:
        return int(m.group(1)) * 1_000
    m = re.search(r"(\d{6,})", s)
    return int(m.group(1)) if m else None


UTIL_SYNONYMS = {
    "air_conditioner": ["dieu hoa", "dieu hoa nhiet do", "may lanh", "ac", "air conditioner"],
    "parking": ["cho de xe", "de xe", "bai xe", "gui xe", "parking", "cho do xe", "do o to"],
    "wifi": ["wifi", "mang", "internet"],
    "washing_machine": ["may giat", "giat"],
}


def norm_utility(span: str) -> str | None:
    if span in UTIL_SYNONYMS:
        return span
    s = strip_accent(span.strip())
    for key, variants in UTIL_SYNONYMS.items():
        for v in variants:
            if re.search(r"\b" + re.escape(v) + r"\b", s):
                return key
    return None


def norm_room_ref(span) -> int | None:
    if isinstance(span, int):
        return span
    s = strip_accent(str(span))
    m = re.search(r"(\d{1,2})", s)
    if m:
        return int(m.group(1))
    for words, v in [(("dau", "nhat"), 1), (("hai",), 2), (("ba",), 3),
                     (("bon", "tu"), 4), (("nam",), 5)]:
        if any(w in s for w in words):
            return v
    return None


def norm_room_type(span) -> str | None:
    if span is None:
        return None
    s = strip_accent(str(span))
    if s in ("phong_tro", "chung_cu_mini", "nha_nguyen_can"):
        return s.upper()
    if "nguyen can" in s:
        return "NHA_NGUYEN_CAN"
    if "chung cu" in s or "ccmn" in s:
        return "CHUNG_CU_MINI"
    if "tro" in s:
        return "PHONG_TRO"
    return None


def norm_area(span) -> float | None:
    if isinstance(span, (int, float)):
        return float(span)
    m = re.search(r"(\d+(?:[.,]\d+)?)", str(span))
    return float(m.group(1).replace(",", ".")) if m else None


def spans_to_slots(entities, sentence: str = "") -> set:
    """[{label,text?,start,end}] (gold hoặc PhoBERT) → {(slot, value)} chuẩn hóa.
    Gold không có "text" (chỉ offset) → cắt từ câu gốc."""
    slots = set()
    for e in entities:
        label = e["label"]
        text = e.get("text") or sentence[e["start"]:e["end"]]
        if not text:
            continue
        if label in ("PRICE_MAX", "PRICE_MIN"):
            v = norm_price(text)
            if v is not None:
                slots.add((label.lower(), v))
        elif label in ("LOCATION", "POI"):
            slots.add((label.lower(), strip_accent(text.strip())))
        elif label == "UTILITY":
            v = norm_utility(text)
            if v is not None:
                slots.add(("utility", v))
        elif label == "ROOM_REF":
            v = norm_room_ref(text)
            if v is not None:
                slots.add(("room_ref", v))
        elif label == "ROOM_TYPE":
            v = norm_room_type(text)
            if v is not None:
                slots.add(("room_type", v))
        elif label == "AREA_MIN":
            v = norm_area(text)
            if v is not None:
                slots.add(("area_min", v))
        elif label in ("DATETIME", "RADIUS"):        # chỉ chấm có/không
            slots.add((label.lower(), "present"))
    return slots


def filters_to_slots(f: dict) -> set:
    """entities JSON của LLM (snake_case, đã chuẩn hóa) → {(slot, value)}."""
    slots = set()
    if f.get("price_max") is not None:
        v = norm_price(f["price_max"])
        if v is not None:
            slots.add(("price_max", v))
    if f.get("price_min") is not None:
        v = norm_price(f["price_min"])
        if v is not None:
            slots.add(("price_min", v))
    for key in ("location", "poi"):
        if f.get(key):
            slots.add((key, strip_accent(str(f[key]).strip())))
    for u in f.get("utilities") or []:
        v = norm_utility(str(u))
        if v is not None:
            slots.add(("utility", v))
    for r in f.get("room_refs") or []:
        v = norm_room_ref(r)
        if v is not None:
            slots.add(("room_ref", v))
    if f.get("room_type"):
        v = norm_room_type(f["room_type"])
        if v is not None:
            slots.add(("room_type", v))
    if f.get("area_min") is not None:
        v = norm_area(f["area_min"])
        if v is not None:
            slots.add(("area_min", v))
    if f.get("datetime"):
        slots.add(("datetime", "present"))
    if f.get("radius_m") is not None:
        slots.add(("radius", "present"))
    return slots


def slot_equal(a, b) -> bool:
    """(slot,value) == (slot,value); location/poi cho phép chứa nhau
    ("cau giay" vs "quan cau giay")."""
    if a[0] != b[0]:
        return False
    if a[0] in ("location", "poi"):
        x, y = str(a[1]), str(b[1])
        return x == y or x in y or y in x
    return a[1] == b[1]


# --- Predictors ---------------------------------------------------------------

def http_json(url, body, headers=None, timeout=60):
    req = urllib.request.Request(url, json.dumps(body).encode(),
                                 {"Content-Type": "application/json", **(headers or {})})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.load(r)


def predict_phobert(text, args):
    t0 = time.perf_counter()
    r = http_json(f"{args.phobert_url}/nlu", {"text": text})
    ms = (time.perf_counter() - t0) * 1000
    return {"intent": r["intent"], "spans": r["entities"],
            "slots": sorted(spans_to_slots(r["entities"])), "latency_ms": ms,
            "tokens_in": 0, "tokens_out": 0}


def gemini_call(args, body):
    url = (f"https://generativelanguage.googleapis.com/v1beta/models/"
           f"{args.model}:generateContent?key={os.environ['GEMINI_API_KEY']}")
    for attempt in range(4):
        try:
            t0 = time.perf_counter()
            r = http_json(url, body)
            return r, (time.perf_counter() - t0) * 1000
        except urllib.error.HTTPError as e:
            detail = e.read().decode(errors="replace")
            if e.code == 429 and attempt < 3:
                m = re.search(r'"retryDelay"\s*:\s*"([0-9.]+)s"', detail)
                wait = float(m.group(1)) if m else 30.0
                print(f"  429 quota — cho {wait:.0f}s roi thu lai...")
                time.sleep(wait + 1)
                continue
            raise RuntimeError(f"Gemini HTTP {e.code}: {detail[:300]}") from e
    raise RuntimeError("Gemini: het luot retry 429")


def usage_tokens(resp):
    u = resp.get("usageMetadata", {})
    return u.get("promptTokenCount", 0), u.get("candidatesTokenCount", 0)


def predict_llm(text, args):
    """Phương án A — prompt JSON y hệt LlmNluServiceImpl."""
    body = {
        "system_instruction": {"parts": [{"text": SYSTEM_PROMPT}]},
        "contents": [{"role": "user", "parts": [{"text": text}]}],
        "generationConfig": {"temperature": 0.0, "maxOutputTokens": 300, "topP": 0.9},
    }
    resp, ms = gemini_call(args, body)
    raw = "".join(p.get("text", "")
                  for p in resp["candidates"][0]["content"].get("parts", []))
    s = raw.replace("```json", "").replace("```", "").strip()
    i, j = s.find("{"), s.rfind("}")
    parsed = json.loads(s[i:j + 1]) if i >= 0 and j > i else {}
    ti, to = usage_tokens(resp)
    ents = parsed.get("entities") or {}
    return {"intent": parsed.get("intent", "out_of_scope"),
            "slots": sorted(filters_to_slots(ents)), "latency_ms": ms,
            "tokens_in": ti, "tokens_out": to}


def predict_fc(text, args):
    """Phương án C — function calling, model bị ép gọi nlu_result."""
    body = {
        "contents": [{"role": "user", "parts": [{"text":
            "Phan tich cau tim phong tro tieng Viet sau: " + text}]}],
        "tools": [FC_TOOL],
        "tool_config": {"function_calling_config": {
            "mode": "ANY", "allowed_function_names": ["nlu_result"]}},
        "generationConfig": {"temperature": 0.0},
    }
    resp, ms = gemini_call(args, body)
    fc_args = {}
    for p in resp["candidates"][0]["content"].get("parts", []):
        if "functionCall" in p:
            fc_args = p["functionCall"].get("args", {})
            break
    ti, to = usage_tokens(resp)
    return {"intent": fc_args.get("intent", "out_of_scope"),
            "slots": sorted(filters_to_slots(fc_args)), "latency_ms": ms,
            "tokens_in": ti, "tokens_out": to}


PREDICTORS = {"phobert": predict_phobert, "llm": predict_llm, "fc": predict_fc}


# --- Chạy & cache -------------------------------------------------------------

def load_jsonl(p):
    with open(p, encoding="utf-8") as f:
        return [json.loads(line) for line in f]


def run_side(side, args):
    if side in ("llm", "fc") and not os.environ.get("GEMINI_API_KEY"):
        raise SystemExit(f"Side '{side}' can GEMINI_API_KEY trong moi truong.")
    OUT.mkdir(exist_ok=True)
    predict = PREDICTORS[side]
    pace = 60.0 / args.rpm if side in ("llm", "fc") else 0.0

    for dataset in ("intent", "ner"):
        rows = load_jsonl(DATA / f"{dataset}_test_real.jsonl")
        cache = OUT / f"{side}_{dataset}.jsonl"
        done = {}
        if cache.exists():
            for line in load_jsonl(cache):
                done[line["text"]] = line
        print(f"[{side}/{dataset}] {len(rows)} cau, da co {len(done)}")
        with cache.open("a", encoding="utf-8") as fout:
            for k, row in enumerate(rows):
                if row["text"] in done:
                    continue
                t0 = time.perf_counter()
                pred = predict(row["text"], args)
                rec = {"text": row["text"], **pred}
                if dataset == "intent":
                    rec["gold_intent"] = row["intent"]
                else:
                    rec["gold_entities"] = row["entities"]
                fout.write(json.dumps(rec, ensure_ascii=False) + "\n")
                fout.flush()
                if (k + 1) % 20 == 0:
                    print(f"  {k + 1}/{len(rows)}")
                time.sleep(max(0.0, pace - (time.perf_counter() - t0)))
    print(f"[{side}] xong — ket qua o {OUT}/")


# --- Metrics & report ---------------------------------------------------------

def intent_metrics(recs):
    golds = [r["gold_intent"] for r in recs]
    preds = [r["intent"] for r in recs]
    acc = sum(g == p for g, p in zip(golds, preds)) / len(recs)
    f1s = []
    for c in INTENTS:
        tp = sum(1 for g, p in zip(golds, preds) if g == c and p == c)
        fp = sum(1 for g, p in zip(golds, preds) if g != c and p == c)
        fn = sum(1 for g, p in zip(golds, preds) if g == c and p != c)
        prec = tp / (tp + fp) if tp + fp else 0.0
        rec = tp / (tp + fn) if tp + fn else 0.0
        f1s.append(2 * prec * rec / (prec + rec) if prec + rec else 0.0)
    return acc, sum(f1s) / len(f1s)


def slot_metrics(recs):
    tp = fp = fn = 0
    for r in recs:
        gold = list(spans_to_slots(r["gold_entities"], r["text"]))
        pred = [tuple(s) for s in r["slots"]]
        used = set()
        for g in gold:
            hit = next((i for i, p in enumerate(pred)
                        if i not in used and slot_equal(g, p)), None)
            if hit is None:
                fn += 1
            else:
                used.add(hit)
                tp += 1
        fp += len(pred) - len(used)
    prec = tp / (tp + fp) if tp + fp else 0.0
    rec = tp / (tp + fn) if tp + fn else 0.0
    f1 = 2 * prec * rec / (prec + rec) if prec + rec else 0.0
    return prec, rec, f1


def span_metrics(recs):
    """Span-F1 strict (label,start,end) — chỉ có nghĩa với side phobert."""
    tp = fp = fn = 0
    for r in recs:
        if "spans" not in r:
            return None
        gold = {(e["label"], e["start"], e["end"]) for e in r["gold_entities"]}
        pred = {(e["label"], e["start"], e["end"]) for e in r["spans"]}
        tp += len(gold & pred)
        fp += len(pred - gold)
        fn += len(gold - pred)
    prec = tp / (tp + fp) if tp + fp else 0.0
    rec = tp / (tp + fn) if tp + fn else 0.0
    return 2 * prec * rec / (prec + rec) if prec + rec else 0.0


def pct(v):
    return f"{v:.3f}" if v is not None else "—"


def p95(xs):
    xs = sorted(xs)
    return xs[min(len(xs) - 1, int(round(0.95 * len(xs))) - 1)]


def report(args):
    sides = [("llm", "A. LLM prompt JSON (GĐ1)"),
             ("phobert", "B. PhoBERT fine-tuned (GĐ2)"),
             ("fc", "C. LLM Function Calling")]
    lines = [
        "# So sánh NLU — SPEC §14.4 (bộ test PROXY, xem cảnh báo trong script)",
        "",
        "| Phương án | Intent Acc | Intent Macro-F1 | Slot-F1 | Span-F1 (strict) | Latency TB | p95 | Chi phí/1000 msg |",
        "|---|---|---|---|---|---|---|---|",
    ]
    for side, label in sides:
        fi, fn_ = OUT / f"{side}_intent.jsonl", OUT / f"{side}_ner.jsonl"
        if not fi.exists() or not fn_.exists():
            lines.append(f"| {label} | — | — | — | — | — | — | chưa chạy |")
            continue
        ri, rn = load_jsonl(fi), load_jsonl(fn_)
        acc, mf1 = intent_metrics(ri)
        _, _, sf1 = slot_metrics(rn)
        spf1 = span_metrics(rn)
        lat = [r["latency_ms"] for r in ri + rn]
        if side == "phobert":
            cost = "≈0đ (self-host CPU)"
        else:
            n = len(ri) + len(rn)
            ti = sum(r["tokens_in"] for r in ri + rn) / n
            to = sum(r["tokens_out"] for r in ri + rn) / n
            usd = (ti * PRICE_IN_PER_M + to * PRICE_OUT_PER_M) / 1e6 * 1000
            cost = (f"~{usd * USD_VND:,.0f}đ (~${usd:.3f}; {ti:.0f} in + {to:.0f} out tok/msg)"
                    .replace(",", "."))
        lines.append(f"| {label} | {pct(acc)} | {pct(mf1)} | {pct(sf1)} | {pct(spf1)} "
                     f"| {sum(lat) / len(lat):.0f}ms | {p95(lat):.0f}ms | {cost} |")
    lines += [
        "",
        "- Test set: `intent_test_real.jsonl` (142 câu) + `ner_test_real.jsonl` (87 câu) — bộ PROXY.",
        "- Slot-F1: mọi phương án quy về cùng dạng (slot, giá_trị) chuẩn hóa; DATETIME/RADIUS chấm có/không.",
        "- Span-F1 strict (label+start+end) chỉ đo được với PhoBERT (LLM không trả span).",
        "- Latency LLM phụ thuộc mạng + tier; PhoBERT đo trên CPU local, không GPU.",
        f"- Chi phí LLM tính theo giá flash-lite ${PRICE_IN_PER_M}/1M in, ${PRICE_OUT_PER_M}/1M out"
        f" (KIỂM TRA lại bảng giá trước khi trích dẫn); free tier = 0đ trong quota.",
    ]
    text = "\n".join(lines)
    OUT.mkdir(exist_ok=True)
    (OUT / "REPORT.md").write_text(text + "\n", encoding="utf-8")
    print(text)
    print(f"\n→ Đã ghi {OUT / 'REPORT.md'}")


if __name__ == "__main__":
    import io
    import sys

    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

    ap = argparse.ArgumentParser()
    ap.add_argument("--side", choices=list(PREDICTORS), help="chay 1 phuong an")
    ap.add_argument("--report", action="store_true", help="in bang tong hop")
    ap.add_argument("--phobert-url", default="http://127.0.0.1:8000")
    ap.add_argument("--model", default="gemini-3.1-flash-lite")
    ap.add_argument("--rpm", type=int, default=14, help="gioi han request/phut cho Gemini")
    a = ap.parse_args()
    if a.side:
        run_side(a.side, a)
    if a.report or not a.side:
        report(a)
