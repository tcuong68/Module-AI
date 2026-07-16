-- =====================================================================
-- Seed data để demo & đo Recall@5 (§14.3). Tọa độ quanh khu vực PTIT
-- (Học viện Công nghệ BCVT, Thanh Xuân, Hà Nội ~ 20.9808, 105.7875).
-- =====================================================================

DELETE FROM room;
DELETE FROM poi;
-- Reset AUTO_INCREMENT để id phòng ổn định (1..N) qua mỗi lần khởi động — tiện demo.
ALTER TABLE room AUTO_INCREMENT = 1;
ALTER TABLE poi  AUTO_INCREMENT = 1;

-- --- POI --------------------------------------------------------------
INSERT INTO poi (name, aliases, type, latitude, longitude) VALUES
 ('Học viện Công nghệ Bưu chính Viễn thông',
  JSON_ARRAY('PTIT','Học viện Bưu chính','HVBCVT','Bưu chính Viễn thông','Bưu chính'),
  'university', 20.9808000, 105.7875000),
 ('Đại học Bách Khoa Hà Nội',
  JSON_ARRAY('Bách Khoa','HUST','ĐHBK','Đại học Bách Khoa'),
  'university', 21.0050000, 105.8430000),
 ('Bến xe Mỹ Đình',
  JSON_ARRAY('Mỹ Đình','bến xe Mỹ Đình'),
  'bus_station', 21.0283000, 105.7787000);

-- --- ROOM (quanh PTIT & Thanh Xuân) ----------------------------------
INSERT INTO room
 (title, price, area, district, address_text, latitude, longitude, status,
  has_air_conditioner, has_parking, has_wifi, has_washing_machine, is_private_bathroom,
  room_type, description, thumbnail)
VALUES
 ('Phòng khép kín ngõ 20 Nguyễn Trãi', 2800000, 22.0, 'Thanh Xuân',
  'Ngõ 20 Nguyễn Trãi, Thanh Xuân', 20.9820000, 105.7900000, 'AVAILABLE',
  TRUE, TRUE, TRUE, FALSE, TRUE, 'PHONG_TRO',
  'Phòng sạch sẽ, thoáng, khép kín, gần PTIT, có điều hòa và chỗ để xe rộng.',
  'https://picsum.photos/seed/room101/400/300'),

 ('Phòng trọ Khương Trung có điều hòa', 2950000, 25.0, 'Thanh Xuân',
  'Khương Trung, Thanh Xuân', 20.9850000, 105.7990000, 'AVAILABLE',
  TRUE, FALSE, TRUE, FALSE, TRUE, 'PHONG_TRO',
  'Phòng rộng 25m2, có điều hòa, wifi mạnh, cách PTIT khoảng 1.2km.',
  'https://picsum.photos/seed/room102/400/300'),

 ('Chung cư mini Chính Kinh full nội thất', 3200000, 28.0, 'Thanh Xuân',
  'Chính Kinh, Thanh Xuân', 20.9880000, 105.8030000, 'AVAILABLE',
  TRUE, TRUE, TRUE, TRUE, TRUE, 'CHUNG_CU_MINI',
  'Chung cư mini đầy đủ nội thất, máy giặt chung, thang máy, an ninh 24/7.',
  'https://picsum.photos/seed/room103/400/300'),

 ('Phòng giá rẻ cho sinh viên Triều Khúc', 1900000, 18.0, 'Thanh Xuân',
  'Triều Khúc, Thanh Xuân', 20.9760000, 105.7960000, 'AVAILABLE',
  FALSE, TRUE, TRUE, FALSE, FALSE, 'PHONG_TRO',
  'Phòng nhỏ giá rẻ, phù hợp sinh viên, gần PTIT, vệ sinh chung.',
  'https://picsum.photos/seed/room104/400/300'),

 ('Phòng đẹp Vũ Trọng Phụng', 3500000, 30.0, 'Thanh Xuân',
  'Vũ Trọng Phụng, Thanh Xuân', 20.9930000, 105.8020000, 'AVAILABLE',
  TRUE, TRUE, TRUE, TRUE, TRUE, 'CHUNG_CU_MINI',
  'Phòng cao cấp, ban công thoáng, đầy đủ tiện nghi, gần trung tâm.',
  'https://picsum.photos/seed/room105/400/300'),

 ('Phòng trọ Cầu Giấy gần Đại học', 3000000, 24.0, 'Cầu Giấy',
  'Dịch Vọng, Cầu Giấy', 21.0350000, 105.7900000, 'AVAILABLE',
  TRUE, TRUE, TRUE, FALSE, TRUE, 'PHONG_TRO',
  'Phòng khu Cầu Giấy, gần nhiều trường đại học, có điều hòa.',
  'https://picsum.photos/seed/room106/400/300'),

 ('Phòng khép kín Hà Đông giá tốt', 2500000, 20.0, 'Hà Đông',
  'Văn Quán, Hà Đông', 20.9700000, 105.7830000, 'AVAILABLE',
  FALSE, TRUE, TRUE, FALSE, TRUE, 'PHONG_TRO',
  'Phòng khép kín, yên tĩnh, chỗ để xe rộng, giá hợp lý.',
  'https://picsum.photos/seed/room107/400/300'),

 ('Nhà nguyên căn Bách Khoa', 6000000, 45.0, 'Hai Bà Trưng',
  'Tạ Quang Bửu, Hai Bà Trưng', 21.0040000, 105.8440000, 'AVAILABLE',
  TRUE, TRUE, TRUE, TRUE, TRUE, 'NHA_NGUYEN_CAN',
  'Nhà nguyên căn gần Đại học Bách Khoa, phù hợp ở ghép.',
  'https://picsum.photos/seed/room108/400/300'),

 ('Phòng đã cho thuê (test status)', 2700000, 21.0, 'Thanh Xuân',
  'Nhân Chính, Thanh Xuân', 20.9900000, 105.8000000, 'RENTED',
  TRUE, TRUE, TRUE, FALSE, TRUE, 'PHONG_TRO',
  'Phòng này đã cho thuê — dùng để test filter status.',
  'https://picsum.photos/seed/room109/400/300');
