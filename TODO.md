# TODO — việc để sau, chưa làm ngay

## Geo/POI — nâng cấp theo mô hình hybrid (bàn ngày 2026-07-17)

Hiện tại: chỉ tra bảng `poi` nội bộ (3 mốc nhập tay), lọc bán kính bằng
`ST_Distance_Sphere`, hiển thị khoảng cách đường chim bay (Haversine).
Hạn chế: POI chưa có trong DB thì không tìm được theo khoảng cách.

### Tầng 2 — Geocoding fallback + cache (ưu tiên làm trước)

Khi `RetrievalService.resolvePoi()` miss:

1. Gọi Geocoding API để lấy lat/lng của span POI:
   - Ứng viên: **Goong.io** / **VietMap** (rẻ, data VN tốt) hoặc **OSM Nominatim**
     (miễn phí, giới hạn 1 req/s — đủ vì có cache). Google Geocoding là phương án cuối (~$5/1000 req).
2. **Validate kết quả trước khi dùng**: tọa độ phải nằm trong bounding box Hà Nội
   (tránh geocode câu cụt/sai chính tả ra tọa độ vớ vẩn — Google luôn cố trả về một cái gì đó).
3. Cache vào bảng `poi` (thêm cột `source` = `'manual'` / `'geocoded'`) — lần sau
   cùng câu hỏi là hit DB, không tốn API call. Bảng POI tự lớn theo nhu cầu thật của user.

Ước lượng: ~50 dòng (1 client geocoding + sửa `resolvePoi` + migration thêm cột `source`).

### Tầng 3 — Quãng đường đi thực cho kết quả hiển thị (tuỳ chọn)

- Giữ nguyên lọc thô toàn DB bằng đường chim bay (như hiện tại — đúng chuẩn ngành).
- Chỉ với top-K phòng sẽ hiển thị (≤5): gọi Distance Matrix / Routes API (Google
  hoặc Goong) lấy quãng đường + thời gian di chuyển thực → RoomCard hiện
  "cách X 1.2km, ~7 phút xe máy" thay vì đường chim bay.
- Cache theo cặp `(room_id, poi_id)` — phòng và mốc đều đứng yên, gọi 1 lần là đủ.
- Nếu không kịp làm: ghi vào mục "hạn chế & hướng phát triển" của báo cáo.

## ML — chuyển từ README (`ml/README.md` §Việc còn lại)

1. Thay `ml/data/*_test_real.jsonl` (proxy tay viết) bằng dữ liệu thật thu thập
   + gán nhãn tay (Doccano/Label Studio) — SPEC §13.3; đánh giá lại DoD.
2. ~~So sánh PhoBERT với baseline LLM (SPEC §14.4)~~ — **XONG 2026-07-18**, đo
   đủ cả 3 phương án (A prompt-JSON, B PhoBERT, C function-calling), bảng ở
   `ml/eval-results/REPORT.md`, harness `ml/eval_nlu_compare.py`. Kết luận:
   B hòa A về intent (acc 0.923), thua ~5đ Slot-F1, thắng ~8–10× latency và
   chi phí ≈0đ. Số liệu trên bộ PROXY — có data thật (mục 1) thì chạy lại
   (xóa `ml/eval-results/*.jsonl` rồi chạy 3 side + `--report`).
3. ~~Bọc FastAPI (`nlu-service/`) + `PhoBertNluServiceImpl` nối vào Spring~~ —
   **XONG 2026-07-18** (SPEC §11 bước 2.3 + 2.4, xem `nlu-service/README.md`).
   PhoBERT là NLU @Primary, timeout 300ms, fallback LLM → rule-based; fast-path
   §6.3 + ghi `path` vào `chat_log` vốn đã có sẵn trong `ChatOrchestrator` từ GĐ1.
   Đã test e2e 6 lượt hội thoại + test fallback khi tắt nlu-service.
