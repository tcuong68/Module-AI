# Flow xử lý chính — Module Chatbot AI tìm phòng trọ (Backend GĐ1)

Tài liệu này tổng hợp và giải thích **luồng xử lý một lượt chat** từ lúc request đến
lúc trả response, cùng các cơ chế cốt lõi (context, retrieval, guardrail). Dùng để
nắm nhanh kiến trúc khi đọc code hoặc bảo vệ đồ án.

> Nguồn code: `com.roomfinder.chat` — điểm vào là `ChatController` →
> `ChatOrchestrator.handle()`. Kiến trúc theo §2.1 của SPEC.

---

## 1. Sơ đồ luồng tổng quát

```
POST /api/v1/chat  { session_id, user_id, message }
        │
        ▼
┌───────────────────────────────────────────────────────────────────────┐
│ ChatOrchestrator.handle()                                              │
│                                                                        │
│  0. Sinh sessionId nếu thiếu · load ChatContext từ Redis               │
│  1. NLU:  message → NluResult { intent, confidence, entities(Filters) } │
│  2. Normalizer:  chuẩn hóa entities (giá/tiện ích/khu vực/thời gian)    │
│  3. Context:  MERGE / OVERRIDE / RESET → activeFilters                  │
│  4. Định tuyến theo intent (switch)                                     │
│        ├─ SEARCH_ROOM / REFINE_SEARCH → handleSearch()                  │
│        ├─ ROOM_DETAIL     → handleRoomDetail()                          │
│        ├─ COMPARE_ROOMS   → handleCompare()                             │
│        ├─ CALCULATE_COST  → handleCalculateCost()                       │
│        ├─ BOOK_APPOINTMENT→ handleBooking()                             │
│        ├─ POLICY_INQUIRY  → câu trả lời mẫu                             │
│        └─ OUT_OF_SCOPE    → câu dẫn hướng lại                           │
│  5. Lưu context · ghi chat_log · build ChatResponse                    │
└───────────────────────────────────────────────────────────────────────┘
        │
        ▼
ChatResponse { reply, intent, rooms[], active_filters, meta{path,latency,...} }
```

---

## 2. Các bước chi tiết trong một lượt chat

### Bước 0 — Nhận request & nạp ngữ cảnh
- `ChatController` (`POST /api/v1/chat`) nhận `ChatRequest`, gọi `orchestrator.handle()`.
- Nếu `session_id` trống → sinh `s-<UUID>`.
- `ContextService.load(sessionId)` đọc `chat:session:{id}` từ **Redis** (JSON).
  Không có → tạo `ChatContext.empty()`. TTL 30 phút, refresh mỗi lượt.

`ChatContext` giữ trạng thái hội thoại: `activeFilters`, `lastResultIds` (danh sách
phòng của lượt trước — nguồn để giải nghĩa "phòng số 1"), `pendingSlot`, `turnCount`, `userId`.

### Bước 1 — NLU (hiểu ngôn ngữ)
`NluService.parse(message)` → `NluResult { intent, confidence, entities }`.

- **Mặc định**: `LlmNluServiceImpl` (`@Primary`) — gọi **Gemini** với prompt yêu cầu trả
  **JSON thuần** theo schema cố định (intent 8 nhãn, confidence, entities).
- **Dự phòng**: nếu LLM lỗi / không có API key / parse JSON hỏng → tự động fallback
  sang `RuleBasedNluService` (regex + từ khóa). ⇒ hệ thống **không bao giờ sập vì NLU**.
- `Intent` là enum đóng 8 nhãn; nhãn lạ được map an toàn về `OUT_OF_SCOPE`.

> Điểm cắm mở rộng: GĐ2 thay bằng PhoBERT chỉ cần chuyển `@Primary`, tầng trên không đổi.

### Bước 2 — Normalizer (chuẩn hóa entity)
`EntityNormalizer.normalize(entities)` biến span "thô" thành giá trị máy đọc được:
- **Tiện ích** → key chuẩn (`air_conditioner`, `parking`, `wifi`, `washing_machine`).
- **Khu vực** → fuzzy match tên quận chuẩn (`LocationNormalizer`).
- **POI** → chỉ trim (việc khớp alias để ở `RetrievalService`).
- **DateTime** → chuẩn ISO nếu đang là span tiếng Việt ("chiều mai 3h").

Đây là **mạng an toàn** cho output của LLM và là tầng gánh việc chính khi lên GĐ2.

### Bước 3 — Context: MERGE / OVERRIDE / RESET
`ContextService.apply(ctx, nlu, message)` hợp nhất entity lượt này vào `activeFilters`:

| Phép | Khi nào | Hành vi |
|---|---|---|
| **RESET** | `search_room` + từ khóa khởi tạo ("tìm phòng khác", "bắt đầu lại"...) | `activeFilters.clear()` rồi mới merge |
| **MERGE** (refine) / **OVERRIDE** (search) | các trường hợp còn lại | `mergeNonNull`: scalar bị **ghi đè**, `utilities` được **union** |

`Filters` dùng chung cho cả "entities lượt này" lẫn "active_filters tích lũy", nên logic
merge chỉ nằm một chỗ. Quy ước then chốt: **`null` = người dùng không nhắc đến** (bỏ qua
khi build query), khác với `false`.

**Hai rule ngữ cảnh đặc biệt** trong orchestrator:
- **Pending slot** (§4.3): nếu lượt trước bot hỏi lại (đang chờ slot) và lượt này người
  dùng cung cấp slot định vị → ép intent thành `SEARCH_ROOM` (coi như tiếp nối tìm kiếm).
- **"Rẻ hơn nữa đi"** (§4.2): `refine_search` không kèm giá mới mà có cue "rẻ hơn" →
  giảm `priceMax` hiện tại theo `cheaperStepPercent` (mặc định 20%).

### Bước 4 — Định tuyến theo intent
`switch (nlu.getIntent())` rẽ nhánh sang các handler. Nhánh quan trọng nhất là tìm kiếm.

---

## 3. Nhánh tìm kiếm — `handleSearch()` (SEARCH_ROOM / REFINE_SEARCH)

Đây là xương sống của module, thể hiện rõ mô hình **RAG có guardrail**.

```
handleSearch()
  │
  ├─ Slot Checker: activeFilters thiếu CẢ giá lẫn khu vực/POI?
  │      → set pendingSlot="location", trả câu hỏi lại (path=CLARIFY), KHÔNG gọi LLM
  │
  ├─ RetrievalService.search(activeFilters)  → RetrievalResult { rooms, relaxed, note }
  │      · lưu lastResultIds = id các phòng trả về
  │
  └─ Chọn đường sinh câu trả lời:
        ├─ rooms rỗng           → template "chưa có phòng phù hợp"        (path=FAST)
        ├─ canFastPath == true  → template liệt kê phòng                  (path=FAST)
        └─ ngược lại            → NlgService.generate() (LLM + guardrail)
              ├─ valid  → dùng câu LLM                                    (path=LLM)
              └─ invalid→ fallback template an toàn                       (path=TEMPLATE)
```

### 3.1. Slot Checker & Ask Clarifying (§4.3)
Nếu `activeFilters.hasAnyLocatorSlot()` = false (không có giá, khu vực, lẫn POI) →
**không đủ để tìm**. Bot đặt `pendingSlot="location"` và hỏi lại người dùng — **tiết kiệm
LLM** và tránh trả kết quả vô nghĩa.

### 3.2. Retrieval (§5) — `RetrievalService.search()`
Filter cứng bằng **SQL trên MySQL**, có hỗ trợ geo và **chiến lược nới lỏng** khi 0 kết quả:

1. **Truy vấn gốc** — filter cứng theo giá/diện tích/tiện ích/khu vực.
   - Nếu có **POI** ("gần PTIT"): resolve POI qua bảng `poi` (khớp name/alias không dấu),
     dùng `ST_Distance_Sphere` với bán kính mặc định 1500m; gán `distance_m` (Haversine) cho card.
2. Nếu rỗng → **nới lỏng theo bậc** (không bao giờ dừng ở "không tìm thấy"):
   - (a) nới `priceMax` **+15%**;
   - (b) nếu tìm theo POI → **gấp đôi bán kính**;
   - (c) **bỏ yêu cầu tiện ích**;
   - (d) fallback cuối: vài phòng gần nhất / cùng khu vực, bỏ mọi ràng buộc phụ.
   - Mỗi lần nới trả kèm `relaxationNote` để câu trả lời nói rõ đã nới gì (`relaxed=true`).

### 3.3. Fast-path vs LLM-path (§6.3) — `canFastPath()`
Bỏ qua LLM (rẻ, nhanh, không rủi ro bịa) khi **tất cả** điều kiện sau đúng:
- intent là `SEARCH_ROOM` / `REFINE_SEARCH`;
- `confidence >= ngưỡng` (fast-path threshold);
- có kết quả (`rooms` không rỗng);
- **không** phải kết quả đã nới lỏng (`relaxed=false`);
- **không** có cue tư vấn ("nên", "tư vấn", "so sánh", "vì sao", "tốt hơn"...).

→ Fast-path trả **template liệt kê phòng**. Ngược lại (cần tư vấn / kết quả đã nới /
confidence thấp) mới đi LLM-path.

### 3.4. NLG (RAG) + Guardrail (§6.1–6.4) — `NlgService.generate()`
- **Serialize** danh sách phòng thành **bảng gạch đầu dòng** (`[#id] | giá | m² | địa chỉ |
  tiện ích | khoảng cách`), kèm bộ lọc & note nới lỏng — LLM đọc chính xác hơn JSON lồng nhau.
- System prompt đặt **quy tắc tuyệt đối**: chỉ nói về phòng trong danh sách, không bịa
  giá/diện tích/mã phòng, luôn nhắc mã dạng `[#id]`.
- Gọi LLM → **HallucinationValidator** kiểm tra:
  - Mọi `[#id]` LLM nhắc phải nằm trong context (`allowedIds`);
  - Mọi con số tiền phải khớp giá một phòng trong context (`allowedPrices`);
  - Sai → `PHANTOM_ROOM_ID` / `PHANTOM_PRICE`.
- **Retry 1 lần** với cảnh báo bổ sung nếu lượt 1 fail. Vẫn fail → trả `valid=false`.

### 3.5. Ghi nhận `path` trung thực
- LLM chạy & qua guardrail → `path=LLM`.
- Guardrail chặn hoặc LLM lỗi → orchestrator **fallback template**, ghi `path=TEMPLATE`
  (không ghi "LLM" khi LLM thực chất không cho ra câu trả lời).
- `hallucinationDetected` phân biệt: guardrail chặn (`true`) vs LLM lỗi kỹ thuật (`false`).

---

## 4. Các nhánh intent khác

| Intent | Handler | Tóm tắt |
|---|---|---|
| `ROOM_DETAIL` | `handleRoomDetail` | Giải nghĩa `room_refs` (ordinal 1-based) theo `lastResultIds`; trả chi tiết 1 phòng. Nhắc phòng không phân giải được → **hỏi lại** thay vì đoán. |
| `COMPARE_ROOMS` | `handleCompare` | Cần ≥2 phòng; thiếu chỉ định → lấy 2 phòng đầu của kết quả trước; xuất bảng so sánh nhanh. |
| `CALCULATE_COST` | `handleCalculateCost` | Ước tính chi phí/tháng = giá phòng + điện/nước/dịch vụ mặc định. |
| `BOOK_APPOINTMENT` | `handleBooking` | **Chỉ thu thập tham số** (phòng + thời gian) & xác nhận — **không ghi DB** (đúng §1.2, chỗ tích hợp API nghiệp vụ). |
| `POLICY_INQUIRY` | `simpleReply` | Câu trả lời mẫu về hợp đồng/đặt cọc. |
| `OUT_OF_SCOPE` | `simpleReply` | Dẫn hướng người dùng về đúng phạm vi (tìm phòng). |

**Nguyên tắc chống "tự tin sai" (`unresolvedRefReply`)**: khi người dùng chỉ đích danh một
phòng mà không phân giải được (`namedARoom` nhưng `resolveRefs` rỗng), bot **hỏi lại** thay
vì đoán sang phòng khác — vì câu trả lời sai nghe vẫn rất tự tin, người dùng khó phát hiện.

`resolveRefs()`: `room_refs` là **thứ tự 1-based** trong `lastResultIds` (nói "phòng 1" =
phòng đầu danh sách trước), hoặc khớp trực tiếp nếu ref trùng id thật.

---

## 5. Bước cuối — Lưu & trả kết quả

Mọi nhánh đều kết thúc bằng:
1. `contextService.save(ctx)` — ghi context (kèm `lastResultIds` mới) về Redis, refresh TTL.
2. `logTurn(...)` — ghi **`chat_log`** (MySQL): message, intent dự đoán, confidence,
   entities, id phòng trả về, `path`, `hallucination_flag`, `latency_ms`. Lỗi ghi log
   **không** làm hỏng lượt chat (bắt exception, chỉ warn).
3. `build(...)` → `ChatResponse`:

```json
{
  "session_id": "...",
  "reply": "câu trả lời",
  "intent": "search_room",
  "rooms": [ { RoomCardDto } ],
  "active_filters": { Filters },
  "meta": {
    "path": "FAST|LLM|TEMPLATE|CLARIFY",
    "relaxed": false,
    "latency_ms": 123,
    "nlu_confidence": 0.96,
    "hallucination_detected": false
  }
}
```

`meta` là bằng chứng để đánh giá/bảo vệ: tỉ lệ fast-path, latency, có nới lỏng không,
guardrail có bắt hallucination không (`chat_log` truy vấn được).

Endpoint phụ:
- `POST /api/v1/chat/reset` — xóa `chat:session:{id}` khỏi Redis.
- `GET /api/v1/chat/health` — health check.

---

## 6. Cơ chế an toàn (không sập) xuyên suốt

| Lớp | Khi lỗi/thiếu | Hành vi thay thế |
|---|---|---|
| NLU (LLM) | lỗi / không key / JSON hỏng | fallback `RuleBasedNluService` |
| Intent lạ | nhãn không thuộc 8 nhãn | map về `OUT_OF_SCOPE` |
| Thiếu slot | không có giá & khu vực | hỏi lại (CLARIFY), không gọi LLM |
| Retrieval 0 kết quả | — | nới lỏng theo bậc (giá → bán kính → tiện ích → fallback) |
| NLG (LLM) | lỗi / guardrail chặn | fallback template liệt kê phòng |
| Guardrail | LLM bịa id/giá | chặn cứng, retry 1 lần, rồi template |
| Redis | đọc/ghi lỗi | tạo context mới / bỏ qua, chỉ warn |
| chat_log | ghi lỗi | bắt exception, không ảnh hưởng response |

> Tổng kết: hệ thống **luôn trả được câu trả lời hợp lệ dựa trên dữ liệu thật**, và
> **không bao giờ để LLM bịa thông tin phòng** lọt tới người dùng — đó là hai đảm bảo
> cốt lõi của module.
