package com.roomfinder.chat.normalizer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

/** Kiểm thử nhận diện tiện ích — §3.3. */
class UtilityNormalizerTest {

    @Test
    void detectsSynonyms() {
        assertEquals(List.of("air_conditioner"), UtilityNormalizer.detectAll("phòng có điều hòa"));
        assertEquals(List.of("air_conditioner"), UtilityNormalizer.detectAll("phòng có máy lạnh"));
        assertTrue(UtilityNormalizer.detectAll("có máy giặt và chỗ để xe")
                .containsAll(List.of("washing_machine", "parking")));
        assertEquals("air_conditioner", UtilityNormalizer.normalizeOne("điều hoà"));
    }

    /**
     * Ca lỗi gốc: biến thể "ac" khớp substring trong "khác"/"các" khiến mọi câu
     * chứa các từ rất phổ biến này bị gán nhầm air_conditioner.
     */
    @Test
    void commonWordsContainingAcAreNotAirConditioner() {
        assertTrue(UtilityNormalizer.detectAll("tìm phòng khác ở Đống Đa").isEmpty());
        assertTrue(UtilityNormalizer.detectAll("cho mình xem các phòng ở Cầu Giấy").isEmpty());
        assertTrue(UtilityNormalizer.detectAll("phòng nào bác thấy ổn").isEmpty());
        assertFalse(UtilityNormalizer.detectAll("tìm phòng khác").contains("air_conditioner"));
    }

    /** "ac" đứng độc lập vẫn phải nhận ra. */
    @Test
    void standaloneAcIsStillRecognized() {
        assertEquals(List.of("air_conditioner"), UtilityNormalizer.detectAll("phòng có ac không"));
    }

    @Test
    void returnsEmptyWhenNoUtility() {
        assertTrue(UtilityNormalizer.detectAll("tìm phòng dưới 3 triệu ở Thanh Xuân").isEmpty());
        assertEquals(null, UtilityNormalizer.normalizeOne("phòng đẹp"));
    }
}
