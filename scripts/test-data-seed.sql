-- =====================================================================
-- Dữ liệu test bổ sung (KHÔNG đụng tới seed gốc trong data.sql).
-- data.sql chạy lại mỗi lần app khởi động (spring.sql.init.mode=always)
-- và sẽ XOÁ TOÀN BỘ room/poi rồi seed lại 8 room + 3 poi cố định.
-- Script này chỉ dùng để bơm thêm case test khi cần (geo, price boundary,
-- tiện ích, status) MÀ KHÔNG PHẢI sửa data.sql — chạy tay qua
-- scripts/seed-test-data.ps1 (hoặc .sh) SAU KHI app đã khởi động.
--
-- Idempotent: mỗi bản ghi test được đánh dấu tiền tố "[TEST]" nên chạy
-- lại nhiều lần không bị trùng — script luôn xoá bản ghi [TEST] cũ trước.
-- =====================================================================

DELETE FROM room WHERE title LIKE '[TEST]%';
DELETE FROM poi  WHERE name  LIKE '[TEST]%';

-- --- POI bổ sung --------------------------------------------------------
INSERT INTO poi (name, aliases, type, latitude, longitude) VALUES
 ('[TEST] Bệnh viện Bạch Mai',
  JSON_ARRAY('Bạch Mai','BV Bạch Mai','Bệnh viện Bạch Mai'),
  'hospital', 21.0011000, 105.8412000);

-- --- ROOM bổ sung — mỗi dòng có mục đích test riêng ---------------------
INSERT INTO room
 (title, price, area, district, address_text, latitude, longitude, status,
  has_air_conditioner, has_parking, has_wifi, has_washing_machine, is_private_bathroom,
  room_type, description, thumbnail)
VALUES
 -- Rất gần PTIT (~150m) — test geo trong bán kính mặc định 1500m
 ('[TEST] Phòng sát PTIT', 2000000, 16.0, 'Thanh Xuân',
  'Sát cổng PTIT, Thanh Xuân', 20.9810000, 105.7878000, 'AVAILABLE',
  TRUE, FALSE, TRUE, FALSE, FALSE, 'PHONG_TRO',
  '[TEST] Dùng để kiểm tra geo "gần PTIT" — cách PTIT khoảng 150m.',
  'https://picsum.photos/seed/testroom01/400/300'),

 -- Cách PTIT ~3km — test loại khỏi bán kính mặc định 1500m
 ('[TEST] Phòng xa PTIT', 2200000, 18.0, 'Thanh Xuân',
  'Cách xa PTIT, Thanh Xuân', 21.0080000, 105.8100000, 'AVAILABLE',
  TRUE, TRUE, TRUE, FALSE, TRUE, 'PHONG_TRO',
  '[TEST] Dùng để kiểm tra geo — cách PTIT khoảng 3km, phải bị loại ở bán kính mặc định.',
  'https://picsum.photos/seed/testroom02/400/300'),

 -- Giá đúng biên 3.000.000 — test PriceNormalizer "dưới 3 triệu"
 ('[TEST] Phòng giá đúng 3 triệu', 3000000, 20.0, 'Thanh Xuân',
  'Test biên giá, Thanh Xuân', 20.9800000, 105.7850000, 'AVAILABLE',
  TRUE, FALSE, TRUE, FALSE, TRUE, 'PHONG_TRO',
  '[TEST] Giá đúng bằng mốc 3.000.000 để test điều kiện < hay <=.',
  'https://picsum.photos/seed/testroom03/400/300'),

 -- Giá rất thấp
 ('[TEST] Phòng giá rất thấp', 800000, 10.0, 'Đống Đa',
  'Test giá thấp, Đống Đa', 21.0150000, 105.8250000, 'AVAILABLE',
  FALSE, FALSE, FALSE, FALSE, FALSE, 'PHONG_TRO',
  '[TEST] Giá thấp bất thường để test cận dưới của khoảng giá.',
  'https://picsum.photos/seed/testroom04/400/300'),

 -- Giá rất cao
 ('[TEST] Phòng giá rất cao', 10000000, 50.0, 'Ba Đình',
  'Test giá cao, Ba Đình', 21.0350000, 105.8200000, 'AVAILABLE',
  TRUE, TRUE, TRUE, TRUE, TRUE, 'NHA_NGUYEN_CAN',
  '[TEST] Giá cao bất thường để test cận trên của khoảng giá.',
  'https://picsum.photos/seed/testroom05/400/300'),

 -- Đầy đủ tất cả tiện ích — test lọc utility AND nhiều điều kiện
 ('[TEST] Phòng đầy đủ tiện ích Cầu Giấy', 3300000, 26.0, 'Cầu Giấy',
  'Test full tiện ích, Cầu Giấy', 21.0330000, 105.7910000, 'AVAILABLE',
  TRUE, TRUE, TRUE, TRUE, TRUE, 'CHUNG_CU_MINI',
  '[TEST] Có đủ: điều hòa, chỗ để xe, wifi, máy giặt, vệ sinh khép kín.',
  'https://picsum.photos/seed/testroom06/400/300'),

 -- Không tiện ích nào — test loại trừ
 ('[TEST] Phòng không tiện ích Hoàng Mai', 1700000, 14.0, 'Hoàng Mai',
  'Test không tiện ích, Hoàng Mai', 20.9700000, 105.8500000, 'AVAILABLE',
  FALSE, FALSE, FALSE, FALSE, FALSE, 'PHONG_TRO',
  '[TEST] Không có điều hòa/chỗ để xe/wifi/máy giặt/vệ sinh riêng.',
  'https://picsum.photos/seed/testroom07/400/300'),

 -- Đã cho thuê — test lọc theo status
 ('[TEST] Phòng đã cho thuê Đống Đa', 2600000, 19.0, 'Đống Đa',
  'Test status RENTED, Đống Đa', 21.0180000, 105.8280000, 'RENTED',
  TRUE, TRUE, TRUE, FALSE, TRUE, 'PHONG_TRO',
  '[TEST] status=RENTED — không được xuất hiện trong kết quả tìm phòng trống.',
  'https://picsum.photos/seed/testroom08/400/300'),

 -- Gần Bách Khoa
 ('[TEST] Phòng gần Bách Khoa', 2900000, 21.0, 'Hai Bà Trưng',
  'Test geo gần Bách Khoa', 21.0055000, 105.8435000, 'AVAILABLE',
  TRUE, FALSE, TRUE, FALSE, TRUE, 'PHONG_TRO',
  '[TEST] Dùng để kiểm tra geo "gần Bách Khoa/HUST".',
  'https://picsum.photos/seed/testroom09/400/300'),

 -- Gần Bến xe Mỹ Đình
 ('[TEST] Phòng gần Bến xe Mỹ Đình', 2400000, 17.0, 'Nam Từ Liêm',
  'Test geo gần Mỹ Đình', 21.0286000, 105.7790000, 'AVAILABLE',
  FALSE, TRUE, TRUE, FALSE, FALSE, 'PHONG_TRO',
  '[TEST] Dùng để kiểm tra geo "gần Bến xe Mỹ Đình".',
  'https://picsum.photos/seed/testroom10/400/300'),

 -- Gần bệnh viện Bạch Mai (poi test ở trên)
 ('[TEST] Phòng gần Bệnh viện Bạch Mai', 2750000, 20.0, 'Đống Đa',
  'Test geo gần Bạch Mai', 21.0015000, 105.8415000, 'AVAILABLE',
  TRUE, TRUE, TRUE, FALSE, TRUE, 'PHONG_TRO',
  '[TEST] Dùng để kiểm tra geo với POI mới thêm: Bệnh viện Bạch Mai.',
  'https://picsum.photos/seed/testroom11/400/300');

SELECT COUNT(*) AS test_rooms_inserted FROM room WHERE title LIKE '[TEST]%';
SELECT COUNT(*) AS test_pois_inserted  FROM poi  WHERE name  LIKE '[TEST]%';
