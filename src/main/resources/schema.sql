-- =====================================================================
-- Schema module Chatbot AI tìm phòng — MVP (Giai đoạn 1)
-- Tương ứng §5.2 (poi), §8 (room + chat_log) của SPEC.
-- Chạy tự động khi khởi động (spring.sql.init.mode=always).
-- =====================================================================

-- --- Bảng ROOM ---------------------------------------------------------
-- Trong hệ thống thật, room đã tồn tại; đây là các cột module chatbot
-- yêu cầu bổ sung (§8). Với MVP ta tạo trọn bảng để chạy độc lập.
CREATE TABLE IF NOT EXISTS room (
    id                   BIGINT PRIMARY KEY AUTO_INCREMENT,
    title                VARCHAR(255) NOT NULL,
    price                BIGINT       NOT NULL,          -- VND
    area                 DECIMAL(6,2),                   -- m2
    district             VARCHAR(100),                   -- "Thanh Xuân", "Cầu Giấy"
    address_text         VARCHAR(255),
    latitude             DECIMAL(10,7),
    longitude            DECIMAL(10,7),
    status               VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    has_air_conditioner  BOOLEAN DEFAULT FALSE,
    has_parking          BOOLEAN DEFAULT FALSE,
    has_wifi             BOOLEAN DEFAULT FALSE,
    has_washing_machine  BOOLEAN DEFAULT FALSE,
    is_private_bathroom  BOOLEAN DEFAULT FALSE,
    room_type            VARCHAR(20),                    -- PHONG_TRO | CHUNG_CU_MINI | NHA_NGUYEN_CAN
    description          TEXT,
    thumbnail            VARCHAR(500),
    -- GĐ3 semantic rerank (SPEC §12.1): embedding của `description`, tính lười
    -- + cache (EmbeddingClient/SemanticRerankService). NULL = chưa tính.
    -- LƯU Ý: nếu đã có DB từ trước khi thêm cột này, chạy tay:
    --   ALTER TABLE room ADD COLUMN description_vector JSON;
    description_vector   JSON,
    -- Index khai báo ngay trong bảng để tránh lỗi "duplicate key name" khi
    -- schema.sql chạy lại mỗi lần khởi động (spring.sql.init.mode=always).
    KEY idx_room_search (status, price, district)
);

-- --- Bảng POI (Point of Interest) — §5.2 -----------------------------
-- Cột `source` (TODO.md "Tầng 2"): 'manual' (seed tay) | 'geocoded' (tự động
-- qua Nominatim khi resolvePoi() miss). LƯU Ý: CREATE TABLE IF NOT EXISTS chỉ
-- áp dụng cho DB mới — nếu bạn đã có DB từ trước khi thêm cột này, chạy tay:
--   ALTER TABLE poi ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'manual';
CREATE TABLE IF NOT EXISTS poi (
    id        BIGINT PRIMARY KEY AUTO_INCREMENT,
    name      VARCHAR(255) NOT NULL,
    aliases   JSON,                     -- ["PTIT","Học viện Bưu chính","HVBCVT"]
    type      VARCHAR(50),              -- university | hospital | bus_station
    latitude  DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    source    VARCHAR(20) NOT NULL DEFAULT 'manual'
);

-- --- Bảng ROOM_VIEW — GĐ3 (Recommendation Engine, SPEC §12.2) ---------
-- "Đã xem" = người dùng hỏi cụ thể về 1 phòng (room_detail/compare_rooms/
-- calculate_cost resolve ra phòng) — không tính việc phòng chỉ xuất hiện
-- trong danh sách kết quả tìm kiếm (tín hiệu quá yếu/nhiễu).
CREATE TABLE IF NOT EXISTS room_view (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT NOT NULL,
    room_id    BIGINT NOT NULL,
    viewed_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_room_view_user (user_id, viewed_at)
);

-- --- Bảng CHAT_LOG — §8 (đánh giá + tái huấn luyện) -------------------
CREATE TABLE IF NOT EXISTS chat_log (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id         VARCHAR(64),
    user_message       TEXT,
    predicted_intent   VARCHAR(32),
    nlu_confidence     FLOAT,
    extracted_entities JSON,
    result_room_ids    JSON,
    path               VARCHAR(8),                    -- FAST | LLM | TEMPLATE | CLARIFY
    hallucination_flag BOOLEAN DEFAULT FALSE,
    latency_ms         INT,
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
