# TODO — việc để sau, chưa làm ngay

## Geo/POI — nâng cấp theo mô hình hybrid (bàn ngày 2026-07-17)

Hiện tại: chỉ tra bảng `poi` nội bộ (3 mốc nhập tay), lọc bán kính bằng
`ST_Distance_Sphere`, hiển thị khoảng cách đường chim bay (Haversine).
Hạn chế: POI chưa có trong DB thì không tìm được theo khoảng cách.

### ~~Tầng 2 — Geocoding fallback + cache~~ — **XONG 2026-07-20**

Cài đặt: `geocoding/GeocodingClient` (+ `NominatimGeocodingClient`, OSM Nominatim
miễn phí), `config/GeocodingProperties` (bounding box Hà Nội — validate trước khi
dùng, chặn tọa độ sai), `Poi.source` (`manual`/`geocoded`) + cột DB tương ứng.
`RetrievalService.resolvePoi()` miss bảng nội bộ → gọi geocode → cache vào `poi`.
Bật/tắt qua `roomfinder.geocoding.enabled` (`GEOCODING_ENABLED`).

**Test e2e thật (2026-07-20)**: "Đại học Giao thông Vận tải" và "Học viện Ngân
hàng" — miss lần đầu → geocode qua Nominatim (~8s) → cache → lần hỏi sau cùng POI
chỉ ~1.4s (hit DB, không gọi API lại). `distance_m` hiển thị đúng trong RoomCard.

**Hạn chế đã quan sát được (ghi lại để không tưởng bở khi bảo vệ)**: Nominatim
(miễn phí) **không có** dữ liệu cho "Đại học Thủy Lợi" dù thử nhiều cách viết
(có/không dấu, tên tiếng Anh đầy đủ) — trong khi vẫn khớp tốt các địa danh lớn
(Hồ Gươm, Bách Khoa). Đây là giới hạn độ phủ dữ liệu OSM với trường/địa danh nhỏ
hơn — nếu gặp thường xuyên trong data thật thu thập được, cân nhắc đổi sang
Goong.io/VietMap (trả phí, data VN tốt hơn) — kiến trúc đã tách qua interface
`GeocodingClient` nên chỉ cần thêm 1 impl mới, không sửa `RetrievalService`.

Chưa làm (để sau nếu cần): Tầng 3 — quãng đường đi thực (Distance Matrix API) cho
top-K phòng hiển thị, xem mục dưới.

### Tầng 3 — Quãng đường đi thực cho kết quả hiển thị (tuỳ chọn)

- Giữ nguyên lọc thô toàn DB bằng đường chim bay (như hiện tại — đúng chuẩn ngành).
- Chỉ với top-K phòng sẽ hiển thị (≤5): gọi Distance Matrix / Routes API (Google
  hoặc Goong) lấy quãng đường + thời gian di chuyển thực → RoomCard hiện
  "cách X 1.2km, ~7 phút xe máy" thay vì đường chim bay.
- Cache theo cặp `(room_id, poi_id)` — phòng và mốc đều đứng yên, gọi 1 lần là đủ.
- Nếu không kịp làm: ghi vào mục "hạn chế & hướng phát triển" của báo cáo.

## ~~GĐ3 — Recommendation Engine + Semantic Rerank~~ — **XONG 2026-07-20**

Cài đặt theo SPEC §12, xem README §1 (bảng kiến trúc) và §8 (quyết định không
dùng Elasticsearch):

- **Seed data**: mở rộng từ 9 → 59 phòng (`scripts/generate_extra_rooms.py`) —
  9 phòng gốc quá ít để rerank/recommend có gì khác biệt để thể hiện.
- **Recommendation** (`service/RecommendationService`, thuần Java, không cần
  model ML): feature vector 9 chiều chuẩn hóa min-max, hồ sơ = trung bình có
  trọng số (half-life cấu hình được) các phòng trong `room_view`. Cần ≥2 lượt
  xem mới personalize (`roomfinder.recommendation.min-views`).
- **Semantic rerank** (`service/SemanticRerankService` + `embedding/`): nlu-service
  thêm endpoint `/embed` (`keepitreal/vietnamese-sbert`, 768d). Kích hoạt bằng
  danh sách từ khóa mô tả định tính (`ChatOrchestrator.DESCRIPTIVE_CUES`) —
  KHÔNG phải mọi câu tìm phòng đều gọi embedding, chỉ khi câu hỏi có nội dung
  định tính (SPEC muốn tránh áp semantic lên câu hỏi thuần cấu trúc).
- Ưu tiên đơn giản khi cả 2 tầng đều áp dụng được: semantic trước, personalization
  sau — KHÔNG blend 2 điểm số (giữ đơn giản, xem README).

**Test e2e thật (2026-07-20)**:
- Recommendation: xem 2 phòng rẻ+điều hòa+chỗ để xe → tìm lại → thứ tự đổi từ
  `[42,4,1,2,3]` (giá) sang `[42,1,11,3,5]` (`ranked_by=personalized`) — phòng
  11 (6tr, đắt) vượt lên trên phòng 4 (rẻ, không điều hòa) nhờ khớp tổ hợp tiện
  ích, đúng ngữ nghĩa content-based KNN.
- Semantic: "Tìm phòng yên tĩnh ở Thanh Xuân" → `ranked_by=semantic`, các phòng
  có mô tả nhắc "yên tĩnh"/"thoáng" (id 26, 11, 42, 5) lên top dù không rẻ nhất.
  Cache `description_vector` hoạt động: lượt 2 cùng câu latency giảm từ ~967ms
  xuống ~190ms (không phải embed lại mô tả phòng).
- Câu không có từ khóa mô tả + không có `user_id`: hành vi giữ nguyên như GĐ1/GĐ2
  (`ranked_by=price`), không có tác dụng phụ.

**Việc còn để sau nếu cần**: mở rộng `DESCRIPTIVE_CUES` khi có data thật cho thấy
cách diễn đạt khác; đánh giá định lượng chất lượng semantic rerank (hiện chỉ
kiểm tra định tính qua demo, chưa có metric kiểu NDCG); nếu catalog vượt hẳn quy
mô đồ án (>10.000 phòng) thì chuyển sang Elasticsearch `dense_vector` như SPEC gốc.

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
