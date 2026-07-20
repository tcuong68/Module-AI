"""Sinh thêm ~50 phòng seed đa dạng cho GĐ3 (semantic rerank + recommendation).

9 phòng gốc trong data.sql không đủ để rerank/recommend có ý nghĩa. Script này
in ra 1 khối `INSERT INTO room (...) VALUES (...);` để dán thêm vào cuối
data.sql (KHÔNG đụng khối 9 phòng gốc — id sẽ nối tiếp nhờ AUTO_INCREMENT).

Chạy: python scripts/generate_extra_rooms.py > /tmp/extra_rooms.sql
(rồi dán nội dung vào cuối src/main/resources/data.sql)
"""

import random

random.seed(7)

# (district, lat, lng) — tọa độ trung tâm gần đúng, đủ dùng cho seed demo.
DISTRICTS = [
    ("Thanh Xuân", 20.9853, 105.8019),
    ("Cầu Giấy", 21.0328, 105.7910),
    ("Hà Đông", 20.9715, 105.7772),
    ("Đống Đa", 21.0170, 105.8250),
    ("Hai Bà Trưng", 21.0080, 105.8570),
    ("Ba Đình", 21.0360, 105.8140),
    ("Hoàng Mai", 20.9750, 105.8500),
    ("Nam Từ Liêm", 21.0020, 105.7650),
    ("Bắc Từ Liêm", 21.0650, 105.7550),
    ("Long Biên", 21.0450, 105.8850),
    ("Tây Hồ", 21.0710, 105.8230),
    ("Hoàn Kiếm", 21.0285, 105.8524),
]

STREET_NAMES = [
    "Ngõ 12", "Ngõ 88", "Ngách 5", "Phố Chợ", "Ngõ 168", "Đường Láng",
    "Ngõ 3", "Ngõ 45", "Đường Bưởi", "Ngõ 27", "Phố mới", "Ngõ 201",
]

ROOM_TYPES = ["PHONG_TRO", "CHUNG_CU_MINI", "NHA_NGUYEN_CAN"]

# Mỗi mẫu mô tả cố tình khác "giọng văn" để semantic rerank có tín hiệu thật
# phân biệt (yên tĩnh/sôi động, mới/cũ, gần chợ/gần trường/gần công viên...).
DESC_TEMPLATES = [
    "Phòng yên tĩnh, thoáng mát, phù hợp người thích không gian riêng tư, ít ồn ào.",
    "Khu sôi động, gần chợ đêm và nhiều quán ăn, tiện cho người thích ra ngoài buổi tối.",
    "Mới xây, nội thất còn mới tinh, sàn gỗ, cửa kính lớn đón sáng tự nhiên.",
    "Nhà cấp 4 lâu năm nhưng chắc chắn, giá mềm, phù hợp sinh viên tiết kiệm.",
    "Gần công viên, không khí trong lành, thích hợp người thích chạy bộ buổi sáng.",
    "Sát mặt đường lớn, tiện buôn bán hoặc đi lại, hơi ồn vào giờ cao điểm.",
    "An ninh tốt, có bảo vệ 24/7, camera khắp khu, phù hợp người ở một mình.",
    "Giờ giấc tự do, không chung chủ, thích hợp người đi làm ca đêm.",
    "Gần trường đại học, nhiều bạn sinh viên thuê, không khí trẻ trung.",
    "Khu dân cư yên bình, ít xe cộ qua lại, phù hợp gia đình nhỏ.",
    "View đẹp, ban công rộng nhìn ra hồ, thích hợp người thích chụp ảnh.",
    "Gần bến xe buýt, di chuyển vào trung tâm thành phố chỉ mất 15 phút.",
    "Hẻm sâu yên tĩnh nhưng xe máy vào tận nơi thoải mái, không phải gửi xe ngoài.",
    "Gần chợ dân sinh, tiện mua đồ ăn hàng ngày, giá cả phải chăng.",
    "Phòng góc, nhiều cửa sổ, thoáng khí tốt, không bị bí bách như phòng trong.",
]

random.shuffle(DESC_TEMPLATES)


def make_room(idx: int):
    district, base_lat, base_lng = random.choice(DISTRICTS)
    lat = round(base_lat + random.uniform(-0.012, 0.012), 7)
    lng = round(base_lng + random.uniform(-0.012, 0.012), 7)
    price = random.choice([1500000, 1800000, 2000000, 2200000, 2500000, 2800000,
                            3000000, 3200000, 3500000, 4000000, 4500000, 5000000,
                            5500000, 6000000, 7000000])
    area = round(random.uniform(14, 45), 1)
    street = random.choice(STREET_NAMES)
    room_type = random.choice(ROOM_TYPES)
    has_ac = random.random() < 0.65
    has_parking = random.random() < 0.75
    has_wifi = random.random() < 0.85
    has_washing = random.random() < 0.45
    has_private_bath = random.random() < 0.7
    desc = DESC_TEMPLATES[idx % len(DESC_TEMPLATES)]
    title_kind = {
        "PHONG_TRO": "Phòng trọ",
        "CHUNG_CU_MINI": "Chung cư mini",
        "NHA_NGUYEN_CAN": "Nhà nguyên căn",
    }[room_type]
    title = f"{title_kind} {street} {district}"
    seed_no = 200 + idx
    return (
        f"('{esc(title)}', {int(price)}, {area}, '{esc(district)}', "
        f"'{esc(street)}, {esc(district)}', {lat}, {lng}, 'AVAILABLE', "
        f"{sql_bool(has_ac)}, {sql_bool(has_parking)}, {sql_bool(has_wifi)}, "
        f"{sql_bool(has_washing)}, {sql_bool(has_private_bath)}, '{room_type}', "
        f"'{esc(desc)}', 'https://picsum.photos/seed/room{seed_no}/400/300')"
    )


def esc(s: str) -> str:
    return s.replace("'", "''")


def sql_bool(b: bool) -> str:
    return "TRUE" if b else "FALSE"


def main(n=50):
    rows = [make_room(i) for i in range(n)]
    print("-- --- ROOM bổ sung cho GĐ3 (semantic rerank + recommendation) ---------")
    print("-- Sinh bằng scripts/generate_extra_rooms.py — 50 phòng đa dạng quận/giá/")
    print("-- tiện ích/mô tả để rerank & recommendation có tín hiệu thật để phân biệt.")
    print("INSERT INTO room")
    print(" (title, price, area, district, address_text, latitude, longitude, status,")
    print("  has_air_conditioner, has_parking, has_wifi, has_washing_machine, is_private_bathroom,")
    print("  room_type, description, thumbnail)")
    print("VALUES")
    print(",\n".join(rows) + ";")


if __name__ == "__main__":
    import io
    import sys
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
    main()
