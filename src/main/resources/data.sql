-- =====================================================================
-- Seed data để demo & đo Recall@5 (§14.3). Tọa độ quanh khu vực PTIT
-- (Học viện Công nghệ BCVT, Thanh Xuân, Hà Nội ~ 20.9808, 105.7875).
-- =====================================================================

DELETE FROM room_view;
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

-- --- ROOM bổ sung cho GĐ3 (semantic rerank + recommendation) ---------
-- Sinh bằng scripts/generate_extra_rooms.py — 50 phòng đa dạng quận/giá/
-- tiện ích/mô tả để rerank & recommendation có tín hiệu thật để phân biệt.
INSERT INTO room
 (title, price, area, district, address_text, latitude, longitude, status,
  has_air_conditioner, has_parking, has_wifi, has_washing_machine, is_private_bathroom,
  room_type, description, thumbnail)
VALUES
('Phòng trọ Ngõ 27 Hoàng Mai', 3500000, 27.2, 'Hoàng Mai', 'Ngõ 27, Hoàng Mai', 20.9730361, 105.8437759, 'AVAILABLE', FALSE, TRUE, TRUE, TRUE, TRUE, 'PHONG_TRO', 'Nhà cấp 4 lâu năm nhưng chắc chắn, giá mềm, phù hợp sinh viên tiết kiệm.', 'https://picsum.photos/seed/room200/400/300'),
('Phòng trọ Ngõ 3 Thanh Xuân', 6000000, 18.1, 'Thanh Xuân', 'Ngõ 3, Thanh Xuân', 20.9967301, 105.791018, 'AVAILABLE', TRUE, TRUE, TRUE, FALSE, TRUE, 'PHONG_TRO', 'Hẻm sâu yên tĩnh nhưng xe máy vào tận nơi thoải mái, không phải gửi xe ngoài.', 'https://picsum.photos/seed/room201/400/300'),
('Phòng trọ Ngõ 27 Long Biên', 3500000, 36.1, 'Long Biên', 'Ngõ 27, Long Biên', 21.0483339, 105.8819375, 'AVAILABLE', TRUE, TRUE, TRUE, FALSE, TRUE, 'PHONG_TRO', 'Phòng góc, nhiều cửa sổ, thoáng khí tốt, không bị bí bách như phòng trong.', 'https://picsum.photos/seed/room202/400/300'),
('Phòng trọ Phố Chợ Nam Từ Liêm', 2000000, 35.7, 'Nam Từ Liêm', 'Phố Chợ, Nam Từ Liêm', 20.998678, 105.7589622, 'AVAILABLE', TRUE, TRUE, FALSE, FALSE, TRUE, 'PHONG_TRO', 'Giờ giấc tự do, không chung chủ, thích hợp người đi làm ca đêm.', 'https://picsum.photos/seed/room203/400/300'),
('Chung cư mini Ngõ 45 Cầu Giấy', 5500000, 24.6, 'Cầu Giấy', 'Ngõ 45, Cầu Giấy', 21.0236336, 105.7890349, 'AVAILABLE', TRUE, TRUE, TRUE, FALSE, FALSE, 'CHUNG_CU_MINI', 'Gần bến xe buýt, di chuyển vào trung tâm thành phố chỉ mất 15 phút.', 'https://picsum.photos/seed/room204/400/300'),
('Phòng trọ Ngõ 45 Ba Đình', 3200000, 32.0, 'Ba Đình', 'Ngõ 45, Ba Đình', 21.0321629, 105.8104043, 'AVAILABLE', FALSE, FALSE, TRUE, FALSE, TRUE, 'PHONG_TRO', 'Gần công viên, không khí trong lành, thích hợp người thích chạy bộ buổi sáng.', 'https://picsum.photos/seed/room205/400/300'),
('Nhà nguyên căn Ngõ 168 Hoàn Kiếm', 4500000, 39.5, 'Hoàn Kiếm', 'Ngõ 168, Hoàn Kiếm', 21.0239306, 105.8542707, 'AVAILABLE', TRUE, TRUE, TRUE, FALSE, TRUE, 'NHA_NGUYEN_CAN', 'Gần chợ dân sinh, tiện mua đồ ăn hàng ngày, giá cả phải chăng.', 'https://picsum.photos/seed/room206/400/300'),
('Chung cư mini Phố Chợ Cầu Giấy', 2500000, 18.0, 'Cầu Giấy', 'Phố Chợ, Cầu Giấy', 21.0326486, 105.784237, 'AVAILABLE', TRUE, FALSE, TRUE, TRUE, TRUE, 'CHUNG_CU_MINI', 'Khu dân cư yên bình, ít xe cộ qua lại, phù hợp gia đình nhỏ.', 'https://picsum.photos/seed/room207/400/300'),
('Nhà nguyên căn Đường Láng Hà Đông', 2500000, 35.9, 'Hà Đông', 'Đường Láng, Hà Đông', 20.9791627, 105.7859356, 'AVAILABLE', FALSE, FALSE, TRUE, TRUE, TRUE, 'NHA_NGUYEN_CAN', 'Gần trường đại học, nhiều bạn sinh viên thuê, không khí trẻ trung.', 'https://picsum.photos/seed/room208/400/300'),
('Phòng trọ Ngõ 12 Đống Đa', 2000000, 22.1, 'Đống Đa', 'Ngõ 12, Đống Đa', 21.0052895, 105.8329462, 'AVAILABLE', TRUE, TRUE, TRUE, FALSE, TRUE, 'PHONG_TRO', 'Khu sôi động, gần chợ đêm và nhiều quán ăn, tiện cho người thích ra ngoài buổi tối.', 'https://picsum.photos/seed/room209/400/300'),
('Nhà nguyên căn Phố mới Bắc Từ Liêm', 5000000, 15.7, 'Bắc Từ Liêm', 'Phố mới, Bắc Từ Liêm', 21.0758054, 105.7587192, 'AVAILABLE', TRUE, TRUE, TRUE, FALSE, TRUE, 'NHA_NGUYEN_CAN', 'Phòng yên tĩnh, thoáng mát, phù hợp người thích không gian riêng tư, ít ồn ào.', 'https://picsum.photos/seed/room210/400/300'),
('Phòng trọ Ngõ 12 Cầu Giấy', 1800000, 24.5, 'Cầu Giấy', 'Ngõ 12, Cầu Giấy', 21.044432, 105.789575, 'AVAILABLE', TRUE, TRUE, TRUE, TRUE, TRUE, 'PHONG_TRO', 'View đẹp, ban công rộng nhìn ra hồ, thích hợp người thích chụp ảnh.', 'https://picsum.photos/seed/room211/400/300'),
('Chung cư mini Ngõ 27 Đống Đa', 2500000, 43.6, 'Đống Đa', 'Ngõ 27, Đống Đa', 21.0197377, 105.8165652, 'AVAILABLE', TRUE, TRUE, TRUE, FALSE, TRUE, 'CHUNG_CU_MINI', 'An ninh tốt, có bảo vệ 24/7, camera khắp khu, phù hợp người ở một mình.', 'https://picsum.photos/seed/room212/400/300'),
('Nhà nguyên căn Ngõ 45 Hai Bà Trưng', 2800000, 37.0, 'Hai Bà Trưng', 'Ngõ 45, Hai Bà Trưng', 20.9980612, 105.8474525, 'AVAILABLE', TRUE, TRUE, FALSE, FALSE, TRUE, 'NHA_NGUYEN_CAN', 'Mới xây, nội thất còn mới tinh, sàn gỗ, cửa kính lớn đón sáng tự nhiên.', 'https://picsum.photos/seed/room213/400/300'),
('Nhà nguyên căn Ngõ 88 Bắc Từ Liêm', 2500000, 44.3, 'Bắc Từ Liêm', 'Ngõ 88, Bắc Từ Liêm', 21.0749395, 105.7611954, 'AVAILABLE', FALSE, TRUE, FALSE, TRUE, TRUE, 'NHA_NGUYEN_CAN', 'Sát mặt đường lớn, tiện buôn bán hoặc đi lại, hơi ồn vào giờ cao điểm.', 'https://picsum.photos/seed/room214/400/300'),
('Phòng trọ Phố Chợ Bắc Từ Liêm', 2200000, 33.0, 'Bắc Từ Liêm', 'Phố Chợ, Bắc Từ Liêm', 21.0716973, 105.750912, 'AVAILABLE', FALSE, TRUE, TRUE, FALSE, TRUE, 'PHONG_TRO', 'Nhà cấp 4 lâu năm nhưng chắc chắn, giá mềm, phù hợp sinh viên tiết kiệm.', 'https://picsum.photos/seed/room215/400/300'),
('Nhà nguyên căn Ngõ 201 Thanh Xuân', 3200000, 22.0, 'Thanh Xuân', 'Ngõ 201, Thanh Xuân', 20.9970505, 105.8088627, 'AVAILABLE', FALSE, TRUE, FALSE, FALSE, FALSE, 'NHA_NGUYEN_CAN', 'Hẻm sâu yên tĩnh nhưng xe máy vào tận nơi thoải mái, không phải gửi xe ngoài.', 'https://picsum.photos/seed/room216/400/300'),
('Chung cư mini Phố Chợ Ba Đình', 3200000, 20.1, 'Ba Đình', 'Phố Chợ, Ba Đình', 21.0259329, 105.8044518, 'AVAILABLE', TRUE, FALSE, TRUE, FALSE, TRUE, 'CHUNG_CU_MINI', 'Phòng góc, nhiều cửa sổ, thoáng khí tốt, không bị bí bách như phòng trong.', 'https://picsum.photos/seed/room217/400/300'),
('Phòng trọ Ngõ 201 Tây Hồ', 7000000, 26.0, 'Tây Hồ', 'Ngõ 201, Tây Hồ', 21.0610347, 105.8268541, 'AVAILABLE', TRUE, TRUE, TRUE, TRUE, FALSE, 'PHONG_TRO', 'Giờ giấc tự do, không chung chủ, thích hợp người đi làm ca đêm.', 'https://picsum.photos/seed/room218/400/300'),
('Phòng trọ Ngách 5 Hoàn Kiếm', 1800000, 36.5, 'Hoàn Kiếm', 'Ngách 5, Hoàn Kiếm', 21.0260001, 105.8500333, 'AVAILABLE', TRUE, TRUE, TRUE, FALSE, TRUE, 'PHONG_TRO', 'Gần bến xe buýt, di chuyển vào trung tâm thành phố chỉ mất 15 phút.', 'https://picsum.photos/seed/room219/400/300'),
('Phòng trọ Đường Bưởi Long Biên', 2800000, 18.8, 'Long Biên', 'Đường Bưởi, Long Biên', 21.0565273, 105.8887744, 'AVAILABLE', TRUE, FALSE, TRUE, TRUE, FALSE, 'PHONG_TRO', 'Gần công viên, không khí trong lành, thích hợp người thích chạy bộ buổi sáng.', 'https://picsum.photos/seed/room220/400/300'),
('Chung cư mini Ngõ 12 Hà Đông', 6000000, 41.1, 'Hà Đông', 'Ngõ 12, Hà Đông', 20.9699114, 105.7861218, 'AVAILABLE', TRUE, TRUE, TRUE, TRUE, TRUE, 'CHUNG_CU_MINI', 'Gần chợ dân sinh, tiện mua đồ ăn hàng ngày, giá cả phải chăng.', 'https://picsum.photos/seed/room221/400/300'),
('Nhà nguyên căn Ngõ 27 Hà Đông', 7000000, 28.2, 'Hà Đông', 'Ngõ 27, Hà Đông', 20.9609617, 105.7829581, 'AVAILABLE', TRUE, FALSE, TRUE, FALSE, TRUE, 'NHA_NGUYEN_CAN', 'Khu dân cư yên bình, ít xe cộ qua lại, phù hợp gia đình nhỏ.', 'https://picsum.photos/seed/room222/400/300'),
('Phòng trọ Ngách 5 Thanh Xuân', 4000000, 14.1, 'Thanh Xuân', 'Ngách 5, Thanh Xuân', 20.9942473, 105.8085361, 'AVAILABLE', TRUE, TRUE, TRUE, TRUE, TRUE, 'PHONG_TRO', 'Gần trường đại học, nhiều bạn sinh viên thuê, không khí trẻ trung.', 'https://picsum.photos/seed/room223/400/300'),
('Phòng trọ Ngõ 12 Bắc Từ Liêm', 1800000, 41.4, 'Bắc Từ Liêm', 'Ngõ 12, Bắc Từ Liêm', 21.0663306, 105.7618225, 'AVAILABLE', TRUE, TRUE, TRUE, FALSE, TRUE, 'PHONG_TRO', 'Khu sôi động, gần chợ đêm và nhiều quán ăn, tiện cho người thích ra ngoài buổi tối.', 'https://picsum.photos/seed/room224/400/300'),
('Nhà nguyên căn Phố Chợ Cầu Giấy', 3500000, 32.8, 'Cầu Giấy', 'Phố Chợ, Cầu Giấy', 21.031438, 105.7937007, 'AVAILABLE', TRUE, TRUE, TRUE, FALSE, TRUE, 'NHA_NGUYEN_CAN', 'Phòng yên tĩnh, thoáng mát, phù hợp người thích không gian riêng tư, ít ồn ào.', 'https://picsum.photos/seed/room225/400/300'),
('Chung cư mini Phố Chợ Bắc Từ Liêm', 2500000, 42.6, 'Bắc Từ Liêm', 'Phố Chợ, Bắc Từ Liêm', 21.0740369, 105.7656123, 'AVAILABLE', TRUE, TRUE, TRUE, TRUE, TRUE, 'CHUNG_CU_MINI', 'View đẹp, ban công rộng nhìn ra hồ, thích hợp người thích chụp ảnh.', 'https://picsum.photos/seed/room226/400/300'),
('Nhà nguyên căn Ngách 5 Cầu Giấy', 1800000, 41.8, 'Cầu Giấy', 'Ngách 5, Cầu Giấy', 21.0259046, 105.7862667, 'AVAILABLE', TRUE, TRUE, TRUE, TRUE, TRUE, 'NHA_NGUYEN_CAN', 'An ninh tốt, có bảo vệ 24/7, camera khắp khu, phù hợp người ở một mình.', 'https://picsum.photos/seed/room227/400/300'),
('Phòng trọ Phố mới Hoàn Kiếm', 3200000, 19.0, 'Hoàn Kiếm', 'Phố mới, Hoàn Kiếm', 21.0393601, 105.8499582, 'AVAILABLE', TRUE, TRUE, TRUE, TRUE, TRUE, 'PHONG_TRO', 'Mới xây, nội thất còn mới tinh, sàn gỗ, cửa kính lớn đón sáng tự nhiên.', 'https://picsum.photos/seed/room228/400/300'),
('Nhà nguyên căn Ngõ 45 Ba Đình', 2800000, 31.2, 'Ba Đình', 'Ngõ 45, Ba Đình', 21.0262127, 105.8107829, 'AVAILABLE', TRUE, TRUE, TRUE, FALSE, TRUE, 'NHA_NGUYEN_CAN', 'Sát mặt đường lớn, tiện buôn bán hoặc đi lại, hơi ồn vào giờ cao điểm.', 'https://picsum.photos/seed/room229/400/300'),
('Chung cư mini Ngách 5 Đống Đa', 2500000, 22.4, 'Đống Đa', 'Ngách 5, Đống Đa', 21.0283207, 105.8155147, 'AVAILABLE', FALSE, FALSE, TRUE, FALSE, FALSE, 'CHUNG_CU_MINI', 'Nhà cấp 4 lâu năm nhưng chắc chắn, giá mềm, phù hợp sinh viên tiết kiệm.', 'https://picsum.photos/seed/room230/400/300'),
('Phòng trọ Đường Láng Hoàng Mai', 4000000, 29.3, 'Hoàng Mai', 'Đường Láng, Hoàng Mai', 20.9665848, 105.8600601, 'AVAILABLE', TRUE, FALSE, TRUE, FALSE, TRUE, 'PHONG_TRO', 'Hẻm sâu yên tĩnh nhưng xe máy vào tận nơi thoải mái, không phải gửi xe ngoài.', 'https://picsum.photos/seed/room231/400/300'),
('Phòng trọ Phố Chợ Thanh Xuân', 1800000, 32.9, 'Thanh Xuân', 'Phố Chợ, Thanh Xuân', 20.9885265, 105.8091391, 'AVAILABLE', TRUE, TRUE, TRUE, FALSE, TRUE, 'PHONG_TRO', 'Phòng góc, nhiều cửa sổ, thoáng khí tốt, không bị bí bách như phòng trong.', 'https://picsum.photos/seed/room232/400/300'),
('Phòng trọ Ngõ 88 Hai Bà Trưng', 5000000, 21.4, 'Hai Bà Trưng', 'Ngõ 88, Hai Bà Trưng', 21.0109209, 105.8460369, 'AVAILABLE', TRUE, TRUE, FALSE, FALSE, TRUE, 'PHONG_TRO', 'Giờ giấc tự do, không chung chủ, thích hợp người đi làm ca đêm.', 'https://picsum.photos/seed/room233/400/300'),
('Chung cư mini Ngõ 12 Đống Đa', 2000000, 22.4, 'Đống Đa', 'Ngõ 12, Đống Đa', 21.0119591, 105.8250021, 'AVAILABLE', TRUE, TRUE, TRUE, FALSE, TRUE, 'CHUNG_CU_MINI', 'Gần bến xe buýt, di chuyển vào trung tâm thành phố chỉ mất 15 phút.', 'https://picsum.photos/seed/room234/400/300'),
('Chung cư mini Phố mới Đống Đa', 6000000, 34.2, 'Đống Đa', 'Phố mới, Đống Đa', 21.0274314, 105.8155508, 'AVAILABLE', TRUE, FALSE, FALSE, TRUE, TRUE, 'CHUNG_CU_MINI', 'Gần công viên, không khí trong lành, thích hợp người thích chạy bộ buổi sáng.', 'https://picsum.photos/seed/room235/400/300'),
('Chung cư mini Ngách 5 Đống Đa', 5000000, 36.6, 'Đống Đa', 'Ngách 5, Đống Đa', 21.0132249, 105.8329749, 'AVAILABLE', FALSE, FALSE, TRUE, TRUE, TRUE, 'CHUNG_CU_MINI', 'Gần chợ dân sinh, tiện mua đồ ăn hàng ngày, giá cả phải chăng.', 'https://picsum.photos/seed/room236/400/300'),
('Nhà nguyên căn Đường Bưởi Hai Bà Trưng', 4500000, 40.1, 'Hai Bà Trưng', 'Đường Bưởi, Hai Bà Trưng', 21.0063378, 105.8463296, 'AVAILABLE', FALSE, TRUE, TRUE, TRUE, TRUE, 'NHA_NGUYEN_CAN', 'Khu dân cư yên bình, ít xe cộ qua lại, phù hợp gia đình nhỏ.', 'https://picsum.photos/seed/room237/400/300'),
('Chung cư mini Đường Bưởi Hai Bà Trưng', 2800000, 44.2, 'Hai Bà Trưng', 'Đường Bưởi, Hai Bà Trưng', 21.0066998, 105.8513178, 'AVAILABLE', TRUE, FALSE, TRUE, TRUE, TRUE, 'CHUNG_CU_MINI', 'Gần trường đại học, nhiều bạn sinh viên thuê, không khí trẻ trung.', 'https://picsum.photos/seed/room238/400/300'),
('Phòng trọ Đường Bưởi Hoàng Mai', 4500000, 20.2, 'Hoàng Mai', 'Đường Bưởi, Hoàng Mai', 20.9650134, 105.8446943, 'AVAILABLE', TRUE, FALSE, TRUE, FALSE, TRUE, 'PHONG_TRO', 'Khu sôi động, gần chợ đêm và nhiều quán ăn, tiện cho người thích ra ngoài buổi tối.', 'https://picsum.photos/seed/room239/400/300'),
('Nhà nguyên căn Ngách 5 Hai Bà Trưng', 4000000, 43.7, 'Hai Bà Trưng', 'Ngách 5, Hai Bà Trưng', 21.0033019, 105.8505874, 'AVAILABLE', FALSE, FALSE, TRUE, FALSE, FALSE, 'NHA_NGUYEN_CAN', 'Phòng yên tĩnh, thoáng mát, phù hợp người thích không gian riêng tư, ít ồn ào.', 'https://picsum.photos/seed/room240/400/300'),
('Nhà nguyên căn Ngõ 201 Nam Từ Liêm', 4500000, 18.5, 'Nam Từ Liêm', 'Ngõ 201, Nam Từ Liêm', 20.9935871, 105.7703797, 'AVAILABLE', TRUE, TRUE, TRUE, TRUE, TRUE, 'NHA_NGUYEN_CAN', 'View đẹp, ban công rộng nhìn ra hồ, thích hợp người thích chụp ảnh.', 'https://picsum.photos/seed/room241/400/300'),
('Nhà nguyên căn Ngõ 27 Bắc Từ Liêm', 1500000, 39.6, 'Bắc Từ Liêm', 'Ngõ 27, Bắc Từ Liêm', 21.0666435, 105.7625097, 'AVAILABLE', FALSE, TRUE, TRUE, TRUE, TRUE, 'NHA_NGUYEN_CAN', 'An ninh tốt, có bảo vệ 24/7, camera khắp khu, phù hợp người ở một mình.', 'https://picsum.photos/seed/room242/400/300'),
('Phòng trọ Phố mới Ba Đình', 3200000, 31.3, 'Ba Đình', 'Phố mới, Ba Đình', 21.0470284, 105.8110388, 'AVAILABLE', TRUE, TRUE, TRUE, TRUE, FALSE, 'PHONG_TRO', 'Mới xây, nội thất còn mới tinh, sàn gỗ, cửa kính lớn đón sáng tự nhiên.', 'https://picsum.photos/seed/room243/400/300'),
('Nhà nguyên căn Ngõ 88 Hoàn Kiếm', 1800000, 34.4, 'Hoàn Kiếm', 'Ngõ 88, Hoàn Kiếm', 21.0388801, 105.8619486, 'AVAILABLE', FALSE, TRUE, TRUE, TRUE, FALSE, 'NHA_NGUYEN_CAN', 'Sát mặt đường lớn, tiện buôn bán hoặc đi lại, hơi ồn vào giờ cao điểm.', 'https://picsum.photos/seed/room244/400/300'),
('Phòng trọ Ngõ 3 Đống Đa', 3200000, 29.3, 'Đống Đa', 'Ngõ 3, Đống Đa', 21.0105377, 105.8285984, 'AVAILABLE', TRUE, TRUE, TRUE, FALSE, TRUE, 'PHONG_TRO', 'Nhà cấp 4 lâu năm nhưng chắc chắn, giá mềm, phù hợp sinh viên tiết kiệm.', 'https://picsum.photos/seed/room245/400/300'),
('Nhà nguyên căn Ngõ 168 Cầu Giấy', 4500000, 37.0, 'Cầu Giấy', 'Ngõ 168, Cầu Giấy', 21.0351929, 105.7869626, 'AVAILABLE', TRUE, TRUE, TRUE, TRUE, TRUE, 'NHA_NGUYEN_CAN', 'Hẻm sâu yên tĩnh nhưng xe máy vào tận nơi thoải mái, không phải gửi xe ngoài.', 'https://picsum.photos/seed/room246/400/300'),
('Chung cư mini Ngõ 45 Hoàn Kiếm', 5000000, 30.0, 'Hoàn Kiếm', 'Ngõ 45, Hoàn Kiếm', 21.0217246, 105.8521507, 'AVAILABLE', TRUE, TRUE, FALSE, TRUE, FALSE, 'CHUNG_CU_MINI', 'Phòng góc, nhiều cửa sổ, thoáng khí tốt, không bị bí bách như phòng trong.', 'https://picsum.photos/seed/room247/400/300'),
('Chung cư mini Ngõ 45 Nam Từ Liêm', 6000000, 29.7, 'Nam Từ Liêm', 'Ngõ 45, Nam Từ Liêm', 20.9904201, 105.7640153, 'AVAILABLE', TRUE, FALSE, FALSE, TRUE, TRUE, 'CHUNG_CU_MINI', 'Giờ giấc tự do, không chung chủ, thích hợp người đi làm ca đêm.', 'https://picsum.photos/seed/room248/400/300'),
('Nhà nguyên căn Phố mới Hoàn Kiếm', 2000000, 32.7, 'Hoàn Kiếm', 'Phố mới, Hoàn Kiếm', 21.0290776, 105.8632658, 'AVAILABLE', TRUE, TRUE, TRUE, FALSE, FALSE, 'NHA_NGUYEN_CAN', 'Gần bến xe buýt, di chuyển vào trung tâm thành phố chỉ mất 15 phút.', 'https://picsum.photos/seed/room249/400/300');
