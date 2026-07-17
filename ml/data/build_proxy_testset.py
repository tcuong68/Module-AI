"""Xây intent_test_real.jsonl + ner_test_real.jsonl — bộ test TAY VIẾT.

QUAN TRỌNG (đọc trước khi dùng số liệu này để bảo vệ): đây là bộ **proxy tạm thời**,
KHÔNG PHẢI dữ liệu thật thu thập từ người dùng (Facebook, chotot...) như SPEC §13.3
yêu cầu. Câu ở đây do người phát triển tự viết, cố tình đa dạng hơn `generate_dataset.py`
(câu cụt, thiếu dấu, viết tắt, teencode, câu dài dòng) để mô phỏng phần nào distribution
shift — nhưng vẫn là dữ liệu "tưởng tượng", không phải hành vi người dùng thật.

TRƯỚC KHI DÙNG SỐ LIỆU ĐÁNH GIÁ NÀY ĐỂ BẢO VỆ CHÍNH THỨC: thay thế/bổ sung bằng câu
thật thu thập + gán nhãn tay (Doccano/Label Studio) theo §13.3.

Cách viết: mỗi câu viết ra literal (không sinh bằng template), kèm danh sách
(substring, label) cho các entity xuất hiện trong câu. Offset được tính bằng
`str.find` ngay tại đây để tránh đếm ký tự thủ công sai.
"""

import json
from pathlib import Path

OUT_DIR = Path(__file__).parent

# Mỗi item: (text, intent, [(substring, label), ...])  — entities rỗng nếu không áp dụng.
ITEMS = [
    # --- search_room --------------------------------------------------
    ("tìm phòng tầm 2tr ở cầu giấy", "search_room", [("2tr", "PRICE_MAX"), ("cầu giấy", "LOCATION")]),
    ("có phòng nào rẻ rẻ thôi khoảng 1tr5 không ạ", "search_room", [("1tr5", "PRICE_MAX")]),
    ("kiếm phòng gần bách khoa cho sinh viên", "search_room", [("bách khoa", "POI")]),
    ("phòng khép kín dưới 3 triệu khu đống đa", "search_room",
     [("dưới 3 triệu", "PRICE_MAX"), ("đống đa", "LOCATION"), ("phòng khép kín", "ROOM_TYPE")]),
    ("cho e hỏi có phòng nào ở tây hồ tầm 4 triệu ko ạ", "search_room",
     [("tây hồ", "LOCATION"), ("4 triệu", "PRICE_MAX")]),
    ("phòng có máy lạnh với wifi giá mềm mềm thôi", "search_room",
     [("máy lạnh", "UTILITY"), ("wifi", "UTILITY")]),
    ("minh can tim phong o ha dong gia duoi 2tr5", "search_room",
     [("ha dong", "LOCATION"), ("duoi 2tr5", "PRICE_MAX")]),
    ("tìm hộ mình phòng gần đại học ngoại thương", "search_room", [("đại học ngoại thương", "POI")]),
    ("phòng nào có chỗ để xe ô tô không nhỉ khu long biên", "search_room",
     [("chỗ để xe", "UTILITY"), ("long biên", "LOCATION")]),
    ("cần phòng rộng trên 25m2 giá cả hợp lý", "search_room", [("trên 25m2", "AREA_MIN")]),
    ("phòng trọ giá sinh viên khu vực hoàng mai", "search_room",
     [("phòng trọ", "ROOM_TYPE"), ("hoàng mai", "LOCATION")]),
    ("tìm phòng", "search_room", []),
    ("có phòng cho thuê không", "search_room", []),
    ("phòng nào view đẹp giá dưới 5tr khu ba đình vậy shop", "search_room",
     [("dưới 5tr", "PRICE_MAX"), ("ba đình", "LOCATION")]),
    ("e muốn thuê phòng gần bến xe mỹ đình", "search_room", [("bến xe mỹ đình", "POI")]),
    ("phòng nào full nội thất giá tầm 3tr5 khu nam từ liêm", "search_room",
     [("3tr5", "PRICE_MAX"), ("nam từ liêm", "LOCATION")]),
    ("phòng có internet ổn định không, ở khu vực hoàn kiếm ấy", "search_room",
     [("internet", "UTILITY"), ("hoàn kiếm", "LOCATION")]),
    ("còn phòng trống không shop ơi", "search_room", []),
    ("mình sinh viên PTIT muốn tìm phòng gần trường giá rẻ", "search_room", [("PTIT", "POI")]),
    ("phòng bắc từ liêm tầm 2 triệu rưỡi có máy giặt không", "search_room",
     [("bắc từ liêm", "LOCATION"), ("2 triệu rưỡi", "PRICE_MAX"), ("máy giặt", "UTILITY")]),
    ("cho mình xin thông tin phòng trống ạ", "search_room", []),
    ("phòng riêng biệt khu hai bà trưng giá tốt", "search_room", [("hai bà trưng", "LOCATION")]),

    # --- refine_search --------------------------------------------------
    ("thế có wifi không", "refine_search", [("wifi", "UTILITY")]),
    ("rẻ hơn được không bạn", "refine_search", []),
    ("có chỗ để xe máy không", "refine_search", [("chỗ để xe", "UTILITY")]),
    ("đổi qua khu cầu giấy đi", "refine_search", [("cầu giấy", "LOCATION")]),
    ("mắc quá, có rẻ hơn không", "refine_search", []),
    ("cần thêm máy giặt nữa", "refine_search", [("máy giặt", "UTILITY")]),
    ("thôi bỏ điều kiện điều hòa đi", "refine_search", [("điều hòa", "UTILITY")]),
    ("tìm lại từ đầu giúp mình", "refine_search", []),
    ("k cần gần ptit nữa, đổi chỗ khác", "refine_search", []),
    ("giá đó hơi cao, thấp hơn xíu được k", "refine_search", []),
    ("ưu tiên có internet với chỗ để xe", "refine_search",
     [("internet", "UTILITY"), ("chỗ để xe", "UTILITY")]),
    ("bắt đầu lại tìm kiếm đi bạn", "refine_search", []),
    ("có máy lạnh k thêm", "refine_search", [("máy lạnh", "UTILITY")]),
    ("khu đó xa quá đổi sang hà đông đi", "refine_search", [("hà đông", "LOCATION")]),

    # --- room_detail --------------------------------------------------
    ("phòng số 2 có gì hay không", "room_detail", [("phòng số 2", "ROOM_REF")]),
    ("cho xem kỹ cái đầu tiên đi", "room_detail", [("cái đầu tiên", "ROOM_REF")]),
    ("phòng 1 diện tích bao nhiêu vậy", "room_detail", [("phòng 1", "ROOM_REF")]),
    ("chi tiết phòng thứ hai giúp mình với", "room_detail", [("phòng thứ hai", "ROOM_REF")]),
    ("cái phòng số 3 địa chỉ chỗ nào", "room_detail", [("phòng số 3", "ROOM_REF")]),
    ("xem full ảnh phòng đầu tiên được không", "room_detail", [("phòng đầu tiên", "ROOM_REF")]),
    ("phòng đó có ban công không nhỉ", "room_detail", []),
    ("cho hỏi thêm về căn số 2", "room_detail", [("số 2", "ROOM_REF")]),

    # --- compare_rooms --------------------------------------------------
    ("so sánh phòng 1 và phòng 2 giúp mình", "compare_rooms",
     [("phòng 1", "ROOM_REF"), ("phòng 2", "ROOM_REF")]),
    ("phòng nào tốt hơn giữa cái đầu tiên với phòng số 3", "compare_rooms",
     [("cái đầu tiên", "ROOM_REF"), ("phòng số 3", "ROOM_REF")]),
    ("2 phòng đó cái nào đáng tiền hơn", "compare_rooms", []),
    ("phòng số 1 với phòng thứ hai khác nhau chỗ nào", "compare_rooms",
     [("phòng số 1", "ROOM_REF"), ("phòng thứ hai", "ROOM_REF")]),
    ("nên chọn cái nào trong 2 phòng vừa gửi", "compare_rooms", []),
    ("so sánh giúp em 2 phòng này với", "compare_rooms", []),

    # --- book_appointment --------------------------------------------------
    ("đặt lịch xem phòng 1 chiều mai nhé", "book_appointment",
     [("phòng 1", "ROOM_REF"), ("chiều mai", "DATETIME")]),
    ("mai mình qua xem phòng được không", "book_appointment", []),
    ("hẹn xem phòng số 2 lúc 9h sáng thứ 3", "book_appointment",
     [("phòng số 2", "ROOM_REF"), ("9h sáng thứ 3", "DATETIME")]),
    ("cho mình đặt lịch xem phòng tối nay lúc 7h", "book_appointment", [("tối nay lúc 7h", "DATETIME")]),
    ("t muốn xem phòng cuối tuần này", "book_appointment", []),
    ("đặt hẹn xem phòng đầu tiên vào sáng mai giúp em", "book_appointment",
     [("phòng đầu tiên", "ROOM_REF"), ("sáng mai", "DATETIME")]),
    ("khi nào xem phòng được ạ", "book_appointment", []),
    ("book lịch xem phòng số 3 chiều thứ 4", "book_appointment",
     [("phòng số 3", "ROOM_REF"), ("chiều thứ 4", "DATETIME")]),

    # --- calculate_cost --------------------------------------------------
    ("ở phòng 1 mỗi tháng hết bao nhiêu tiền tất cả", "calculate_cost", [("phòng 1", "ROOM_REF")]),
    ("tính giúp em chi phí điện nước phòng số 2", "calculate_cost", [("phòng số 2", "ROOM_REF")]),
    ("tổng chi phí hàng tháng khoảng bao nhiêu vậy", "calculate_cost", []),
    ("thuê phòng đó xong đóng thêm phí gì không", "calculate_cost", []),
    ("giá phòng đã bao gồm điện nước chưa", "calculate_cost", []),
    ("phòng thứ hai tính đủ dịch vụ hết nhiêu tiền", "calculate_cost", [("phòng thứ hai", "ROOM_REF")]),

    # --- policy_inquiry --------------------------------------------------
    ("hợp đồng ký tối thiểu mấy tháng vậy", "policy_inquiry", []),
    ("đặt cọc bao nhiêu là đủ ạ", "policy_inquiry", []),
    ("lỡ muốn hủy hợp đồng sớm thì sao", "policy_inquiry", []),
    ("cọc có được hoàn lại không nếu trả phòng sớm", "policy_inquiry", []),
    ("gia hạn hợp đồng cần làm thủ tục gì", "policy_inquiry", []),
    ("quy định phạt cọc thế nào vậy ạ", "policy_inquiry", []),
    ("thuê tối thiểu bao lâu vậy shop", "policy_inquiry", []),

    # --- out_of_scope --------------------------------------------------
    ("chào bạn", "out_of_scope", []),
    ("bạn là ai vậy", "out_of_scope", []),
    ("hôm nay trời đẹp ghê", "out_of_scope", []),
    ("cảm ơn nha", "out_of_scope", []),
    ("kể chuyện cười đi bot", "out_of_scope", []),
    ("bạn ăn cơm chưa", "out_of_scope", []),
    ("test thử xem bot trả lời gì", "out_of_scope", []),
    ("1 + 1 = mấy vậy", "out_of_scope", []),
    ("byeee", "out_of_scope", []),

    # --- thêm đợt 2: đa dạng hơn (viết tắt, câu dài, radius, room_type) ------
    # search_room
    ("a oi tim phong dum e o dong da duoi 2 trieu", "search_room",
     [("dong da", "LOCATION"), ("duoi 2 trieu", "PRICE_MAX")]),
    ("phòng nào cách ptit khoảng 2km đổ lại không shop", "search_room",
     [("ptit", "POI"), ("2km", "RADIUS")]),
    ("mình cần phòng bán kính 1km quanh đại học quốc gia", "search_room",
     [("1km", "RADIUS"), ("đại học quốc gia", "POI")]),
    ("tìm chung cư mini giá dưới 4tr5 khu times city", "search_room",
     [("chung cư mini", "ROOM_TYPE"), ("dưới 4tr5", "PRICE_MAX"), ("times city", "POI")]),
    ("nhà nguyên căn cho nhóm 3-4 người khu hà đông", "search_room",
     [("nhà nguyên căn", "ROOM_TYPE"), ("hà đông", "LOCATION")]),
    ("e la sv nam nhat dang tim tro gan truong gia re", "search_room", []),
    ("có phòng nào tầm giá sinh viên không ạ, khoảng 1tr8 thôi", "search_room",
     [("1tr8", "PRICE_MAX")]),
    ("phòng full đồ giá 3 triệu khu vực đống đa hoặc cầu giấy đều được", "search_room",
     [("3 triệu", "PRICE_MAX"), ("đống đa", "LOCATION")]),
    ("tìm phòng có bãi để ô tô khu vực long biên", "search_room",
     [("bãi", "UTILITY"), ("long biên", "LOCATION")]),
    ("phòng nào diện tích tầm 30 m² không nhỉ", "search_room", [("30 m²", "AREA_MIN")]),
    ("cho hỏi còn phòng nào trống ở khu ngoại thương k ạ", "search_room", [("ngoại thương", "POI")]),
    ("e tim phong o gan dai hoc kinh te quoc dan", "search_room", [("dai hoc kinh te quoc dan", "POI")]),

    # refine_search
    ("thôi khỏi cần điều hòa cũng được", "refine_search", [("điều hòa", "UTILITY")]),
    ("giá cao quá bớt được tí nào không bạn", "refine_search", []),
    ("chuyển hướng tìm bên khu tây hồ thử xem", "refine_search", [("tây hồ", "LOCATION")]),
    ("giữ nguyên giá nhưng đổi khu vực sang long biên", "refine_search", [("long biên", "LOCATION")]),
    ("làm lại từ đầu giúp em với", "refine_search", []),
    ("thêm điều kiện có wifi mạnh vào", "refine_search", [("wifi", "UTILITY")]),
    ("k lấy khu đó nữa, tìm chỗ khác đi", "refine_search", []),
    ("giảm bán kính xuống còn 1km thôi", "refine_search", [("1km", "RADIUS")]),

    # room_detail
    ("căn đầu tiên có thang máy không ta", "room_detail", [("đầu tiên", "ROOM_REF")]),
    ("phòng số 2 hình ảnh thực tế không đó", "room_detail", [("phòng số 2", "ROOM_REF")]),
    ("cho e xin thêm ảnh phòng 3", "room_detail", [("phòng 3", "ROOM_REF")]),
    ("phòng thứ hai an ninh sao rồi", "room_detail", [("phòng thứ hai", "ROOM_REF")]),
    ("phòng đó gần chợ không", "room_detail", []),
    ("thông tin chi tiết căn số 1 với", "room_detail", [("số 1", "ROOM_REF")]),

    # compare_rooms
    ("giữa phòng số 1 và cái đầu tiên thì chọn cái nào", "compare_rooms",
     [("phòng số 1", "ROOM_REF"), ("cái đầu tiên", "ROOM_REF")]),
    ("phòng 2 với phòng 3 cái nào rộng hơn", "compare_rooms",
     [("phòng 2", "ROOM_REF"), ("phòng 3", "ROOM_REF")]),
    ("cho em xin bảng so sánh 2 phòng vừa rồi", "compare_rooms", []),
    ("hai phòng đó chênh nhau bao nhiêu tiền", "compare_rooms", []),

    # book_appointment
    ("cho mình lịch xem phòng vào chủ nhật này", "book_appointment", []),
    ("hẹn xem phòng 2 lúc 10h sáng thứ 5 nhé", "book_appointment",
     [("phòng 2", "ROOM_REF"), ("10h sáng thứ 5", "DATETIME")]),
    ("đặt xem phòng số 1 giúp em vào 8h tối mai", "book_appointment",
     [("phòng số 1", "ROOM_REF"), ("8h tối mai", "DATETIME")]),
    ("t rảnh chiều mai qua xem phòng luôn được k", "book_appointment", [("chiều mai", "DATETIME")]),
    ("sắp lịch xem phòng đầu tiên giúp mình với ạ", "book_appointment", [("phòng đầu tiên", "ROOM_REF")]),

    # calculate_cost
    ("tổng tiền phải đóng tháng đầu là bao nhiêu", "calculate_cost", []),
    ("phòng 2 cộng hết điện nước dịch vụ khoảng bao nhiêu", "calculate_cost", [("phòng 2", "ROOM_REF")]),
    ("có tính phí gửi xe riêng không hay đã gồm trong giá", "calculate_cost", []),
    ("ước tính giúp em chi phí ở phòng số 3", "calculate_cost", [("phòng số 3", "ROOM_REF")]),

    # policy_inquiry
    ("nếu ở k đủ 6 tháng có bị phạt không", "policy_inquiry", []),
    ("thủ tục ký hợp đồng cần giấy tờ gì", "policy_inquiry", []),
    ("cho hỏi chính sách khi muốn chuyển phòng khác", "policy_inquiry", []),
    ("phí dịch vụ có được ghi rõ trong hợp đồng không", "policy_inquiry", []),

    # out_of_scope
    ("ê bot ơi", "out_of_scope", []),
    ("bạn thích màu gì", "out_of_scope", []),
    ("có biết đá bóng không", "out_of_scope", []),
    ("ok cảm ơn nhiều", "out_of_scope", []),
    ("hihi", "out_of_scope", []),

    # --- thêm đợt 3: đẩy thêm entity cho NER + đa dạng thêm cho intent ------
    ("phòng khép kín dưới 2tr8 gần đại học quốc gia bán kính 1km", "search_room",
     [("phòng khép kín", "ROOM_TYPE"), ("dưới 2tr8", "PRICE_MAX"),
      ("đại học quốc gia", "POI"), ("1km", "RADIUS")]),
    ("tìm nhà nguyên căn khu hoàn kiếm giá từ 5 triệu trở lên", "search_room",
     [("nhà nguyên căn", "ROOM_TYPE"), ("hoàn kiếm", "LOCATION"), ("5 triệu", "PRICE_MIN")]),
    ("phòng có wifi, chỗ để xe, máy giặt ở khu bắc từ liêm", "search_room",
     [("wifi", "UTILITY"), ("chỗ để xe", "UTILITY"), ("máy giặt", "UTILITY"), ("bắc từ liêm", "LOCATION")]),
    ("mình cần phòng trên 20m2 gần bến xe mỹ đình dưới 3tr5", "search_room",
     [("trên 20m2", "AREA_MIN"), ("bến xe mỹ đình", "POI"), ("dưới 3tr5", "PRICE_MAX")]),
    ("phòng khu vực nam từ liêm tầm 2tr đến 3tr", "search_room",
     [("nam từ liêm", "LOCATION"), ("2tr", "PRICE_MIN")]),
    ("cho hỏi phòng nào gần học viện bưu chính viễn thông không", "search_room",
     [("học viện bưu chính viễn thông", "POI")]),
    ("bớt giá xuống dưới 2 triệu rưỡi được không", "refine_search", [("dưới 2 triệu rưỡi", "PRICE_MAX")]),
    ("mở rộng bán kính lên 3km giúp mình", "refine_search", [("3km", "RADIUS")]),
    ("đổi loại phòng sang chung cư mini xem sao", "refine_search", [("chung cư mini", "ROOM_TYPE")]),
    ("thêm điều kiện diện tích trên 22m2", "refine_search", [("trên 22m2", "AREA_MIN")]),
    ("phòng số 1 và phòng số 3 hẹn xem cùng lúc được không, chiều mai nhé",
     "book_appointment", [("phòng số 1", "ROOM_REF"), ("chiều mai", "DATETIME")]),
    ("mình muốn xem cả phòng đầu tiên và phòng thứ hai vào sáng thứ 2",
     "compare_rooms", [("phòng đầu tiên", "ROOM_REF"), ("phòng thứ hai", "ROOM_REF")]),
    ("phòng số 2 có cho nuôi thú cưng không", "room_detail", [("phòng số 2", "ROOM_REF")]),
    ("giá phòng 1 đã gồm phí quản lý chưa", "calculate_cost", [("phòng 1", "ROOM_REF")]),
]


def build():
    intent_rows = []
    ner_rows = []
    for text, intent, ents in ITEMS:
        intent_rows.append({"text": text, "intent": intent})
        if ents:
            entities = []
            cursor = 0
            for substring, label in ents:
                idx = text.find(substring, cursor)
                if idx == -1:
                    idx = text.find(substring)
                if idx == -1:
                    raise ValueError(f"Khong tim thay '{substring}' trong: {text}")
                entities.append({"start": idx, "end": idx + len(substring), "label": label})
            ner_rows.append({"text": text, "entities": entities})
    return intent_rows, ner_rows


def write_jsonl(path: Path, rows):
    with path.open("w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")


def main():
    import io, sys
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

    intent_rows, ner_rows = build()
    write_jsonl(OUT_DIR / "intent_test_real.jsonl", intent_rows)
    write_jsonl(OUT_DIR / "ner_test_real.jsonl", ner_rows)
    print(f"intent_test_real.jsonl: {len(intent_rows)} cau")
    print(f"ner_test_real.jsonl:    {len(ner_rows)} cau (co >=1 entity)")


if __name__ == "__main__":
    main()
