package com.roomfinder.chat.normalizer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Kiểm thử Normalizer — phục vụ chỉ số Exact-match accuracy (§14.1). */
class PriceNormalizerTest {

    @Test
    void normalizesCommonVietnameseForms() {
        assertEquals(3_000_000L, PriceNormalizer.normalize("3 triệu"));
        assertEquals(3_000_000L, PriceNormalizer.normalize("3 củ"));
        assertEquals(3_500_000L, PriceNormalizer.normalize("3tr5"));
        assertEquals(3_500_000L, PriceNormalizer.normalize("3 triệu 5"));
        assertEquals(500_000L, PriceNormalizer.normalize("500k"));
        assertEquals(2_000_000L, PriceNormalizer.normalize("2 tr"));
        assertEquals(3_000_000L, PriceNormalizer.normalize("3000000"));
    }

    @Test
    void returnsNullWhenNoPrice() {
        assertNull(PriceNormalizer.normalize("phòng đẹp gần trường"));
    }
}
