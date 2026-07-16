package com.roomfinder.chat.service;

import com.roomfinder.chat.domain.Room;
import com.roomfinder.chat.model.ValidationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Kiểm thử guardrail §6.4 — bằng chứng DoD-4 (hallucination = 0). */
class HallucinationValidatorTest {

    private final HallucinationValidator validator = new HallucinationValidator();

    private Room room(long id, long price) {
        Room r = new Room();
        r.setId(id);
        r.setPrice(price);
        return r;
    }

    @Test
    void acceptsOutputThatOnlyReferencesContext() {
        var ctx = List.of(room(101, 2_800_000), room(205, 2_950_000));
        ValidationResult v = validator.validate(
                "Mình gợi ý phòng [#101] giá 2,800,000đ và [#205] giá 2,950,000đ nhé.", ctx);
        assertTrue(v.ok());
    }

    @Test
    void rejectsPhantomRoomId() {
        var ctx = List.of(room(101, 2_800_000));
        ValidationResult v = validator.validate("Có phòng [#999] rất đẹp nhé.", ctx);
        assertFalse(v.ok());
        assertTrue(v.reason().startsWith("PHANTOM_ROOM_ID"));
    }

    @Test
    void rejectsPhantomPrice() {
        var ctx = List.of(room(101, 2_800_000));
        ValidationResult v = validator.validate("Phòng [#101] chỉ 1,000,000đ thôi.", ctx);
        assertFalse(v.ok());
        assertTrue(v.reason().startsWith("PHANTOM_PRICE"));
    }
}
