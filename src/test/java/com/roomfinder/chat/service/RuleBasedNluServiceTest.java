package com.roomfinder.chat.service;

import com.roomfinder.chat.domain.Intent;
import com.roomfinder.chat.model.Filters;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Kiểm thử NLU rule-based — nhánh dự phòng khi LLM lỗi/timeout. Vì nhánh này
 * chỉ chạy khi Gemini hỏng, nó không được test e2e che phủ; các ca dưới đây là
 * lưới an toàn duy nhất cho nó.
 */
class RuleBasedNluServiceTest {

    private final RuleBasedNluService nlu = new RuleBasedNluService();

    private Filters parse(String msg) {
        return nlu.parse(msg).getEntities();
    }

    @Test
    void treatsBarePriceAsBudgetCeiling() {
        assertEquals(3_000_000L, parse("Tìm phòng 3 triệu").getPriceMax());
        assertNull(parse("Tìm phòng 3 triệu").getPriceMin());
    }

    @Test
    void parsesUpperBoundCues() {
        assertEquals(3_000_000L, parse("Tìm phòng dưới 3 triệu ở Thanh Xuân").getPriceMax());
        assertEquals(3_000_000L, parse("Phòng tối đa 3 củ").getPriceMax());
        assertEquals(3_000_000L, parse("Phòng không quá 3tr").getPriceMax());
        assertEquals(3_000_000L, parse("Phòng 3 triệu trở xuống").getPriceMax());
    }

    /** Ca lỗi gốc: "trên 3 củ" từng bị gán price_max → trả kết quả y hệt "dưới 3 củ". */
    @Test
    void parsesLowerBoundCues() {
        assertEquals(3_000_000L, parse("Trên 3 củ thì sao").getPriceMin());
        assertNull(parse("Trên 3 củ thì sao").getPriceMax());

        assertEquals(3_000_000L, parse("trên 3 triệu thì sao").getPriceMin());
        assertEquals(3_000_000L, parse("Phòng hơn 3 triệu").getPriceMin());
        assertEquals(3_000_000L, parse("Phòng từ 3 triệu trở lên").getPriceMin());
        assertEquals(3_000_000L, parse("Phòng 3 triệu trở lên").getPriceMin());
        assertEquals(3_000_000L, parse("Ngân sách tối thiểu 3 triệu").getPriceMin());
    }

    /** "Nam/Bắc Từ Liêm" chứa "từ" — quét cả câu sẽ gán nhầm price_min. */
    @Test
    void districtNameContainingTuIsNotAPriceCue() {
        Filters f = parse("Tìm phòng dưới 3 triệu ở Nam Từ Liêm");
        assertEquals(3_000_000L, f.getPriceMax());
        assertNull(f.getPriceMin());
        assertEquals("Nam Từ Liêm", f.getLocation());
    }

    /**
     * price_min một mình phải đủ để coi là ý định tìm phòng. hasAnyLocatorSlot()
     * chỉ xét price_max, nên câu chỉ có cận dưới từng rơi vào out_of_scope.
     */
    @Test
    void lowerBoundOnlyStillCountsAsSearch() {
        assertEquals(Intent.SEARCH_ROOM, nlu.parse("Trên 3 củ thì sao").getIntent());
    }

    /**
     * NLU rule-based trước đây không trích room_refs, nên ở chế độ fallback mọi
     * tham chiếu thứ tự đều hỏng — "tính chi phí phòng 2" âm thầm trả lời về phòng khác.
     */
    @Test
    void extractsRoomRefs() {
        assertEquals(List.of(1), parse("Chi tiết phòng 1").getRoomRefs());
        assertEquals(List.of(2), parse("Tính chi phí phòng 2").getRoomRefs());
        assertEquals(List.of(1), parse("Cho mình xem phòng số 1").getRoomRefs());
        assertEquals(List.of(3), parse("Phòng thứ 3 thế nào").getRoomRefs());
        assertEquals(List.of(1, 2), parse("So sánh phòng 1 và 2").getRoomRefs());
        assertEquals(List.of(1, 2, 3), parse("So sánh phòng 1, 2 và 3").getRoomRefs());
        assertEquals(List.of(1), parse("Đặt lịch xem phòng 1 chiều mai lúc 3h").getRoomRefs());
    }

    /** Số đi kèm đơn vị là giá/diện tích, không phải số thứ tự phòng. */
    @Test
    void priceAndAreaNumbersAreNotRoomRefs() {
        assertEquals(List.of(), parse("Tìm phòng 3 triệu ở Thanh Xuân").getRoomRefs());
        assertEquals(List.of(), parse("Tìm phòng dưới 3 triệu").getRoomRefs());
        assertEquals(List.of(), parse("Có phòng 20m2 không").getRoomRefs());
        assertEquals(List.of(), parse("Phòng 10 triệu ở Cầu Giấy").getRoomRefs());
    }

    /** "trên" của diện tích không được kéo giá thành price_min. */
    @Test
    void areaCueDoesNotFlipPriceDirection() {
        Filters f = parse("Phòng trên 30m2 dưới 3 triệu");
        assertEquals(3_000_000L, f.getPriceMax());
        assertNull(f.getPriceMin());
        assertEquals(30.0, f.getAreaMin());
    }
}
