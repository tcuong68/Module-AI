# nlu-service — PhoBERT NLU qua FastAPI (SPEC §11 bước 2.3)

Bọc 2 model đã train ở GĐ2 (`ml/out-intent`, `ml/out-ner`) thành HTTP service.
Spring Boot gọi qua `PhoBertNluServiceImpl` (bước 2.4, @Primary): RestClient
timeout 300ms, service chết → fallback `LlmNluServiceImpl` → rule-based.
Cấu hình phía Spring: khối `roomfinder.nlu` trong `application.yml`
(`NLU_URL`, `NLU_ENABLED`).

## API

```
GET  /health   → {"status":"ok", ...}
POST /nlu      {"text": "tìm phòng tầm 2tr ở cầu giấy"}
→ {
    "intent": "search_room",
    "confidence": 0.97,
    "entities": [
      {"label": "PRICE_MAX", "text": "2tr",      "start": 14, "end": 17, "score": 0.93},
      {"label": "LOCATION",  "text": "cầu giấy", "start": 20, "end": 28, "score": 0.92}
    ]
  }
```

**Entity là span thô** (offset ký tự trên văn bản gốc) — service KHÔNG chuẩn hóa
giá trị ("2tr" → 2000000 là việc của `EntityNormalizer` phía Java, §3.3). Lý do:
giữ 1 nơi duy nhất biết luật chuẩn hóa; SPEC dòng 804 viết `normalize(entities)`
trong ví dụ Python nhưng backend đã có sẵn tầng normalizer đầy đủ, không nhân đôi.

## Chạy cục bộ (dev)

Dùng chung venv với `ml/` (đã có torch/transformers; cài thêm fastapi+uvicorn):

```bash
cd nlu-service
../ml/.venv/Scripts/pip install fastapi uvicorn   # 1 lần
../ml/.venv/Scripts/python -m uvicorn app:app --host 127.0.0.1 --port 8000
```

Cần JDK trên PATH/JAVA_HOME (VnCoreNLP chạy trên JVM). Khởi động mất ~30s
(nạp 2 model ~1GB + JVM). Yêu cầu `ml/out-intent`, `ml/out-ner` đã train xong
(xem `ml/README.md`).

## Chạy bằng Docker

```bash
docker compose up nlu    # build context là backend root, xem docker-compose.yml
```

Model mount qua volume `./ml/out-*` (không bake vào image — mỗi model ~515MB).

## Kết quả smoke test (2026-07-18, 10 câu tay viết đủ 8 intent)

10/10 đúng intent (confidence 0.92–0.97), entity bắt đúng cả câu không dấu
("ha dong", "2tr5"), latency 60–150ms/request trên CPU. Hai lỗi NER nhỏ quan sát
được: "phòng khép kín" (ROOM_TYPE) không bắt được; "sinh viên" bị gán UTILITY
score thấp (0.64) — lỗi model (train synthetic), không phải lỗi service; sẽ cải
thiện khi có data thật (xem TODO.md).
