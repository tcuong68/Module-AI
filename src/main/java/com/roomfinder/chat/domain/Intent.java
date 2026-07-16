package com.roomfinder.chat.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Tập intent đóng (8 nhãn) — §3.1 của SPEC.
 * Mã JSON (snake_case) là hợp đồng với LLM/PhoBERT.
 */
public enum Intent {
    SEARCH_ROOM("search_room"),
    REFINE_SEARCH("refine_search"),
    ROOM_DETAIL("room_detail"),
    COMPARE_ROOMS("compare_rooms"),
    BOOK_APPOINTMENT("book_appointment"),
    CALCULATE_COST("calculate_cost"),
    POLICY_INQUIRY("policy_inquiry"),
    OUT_OF_SCOPE("out_of_scope");

    private final String code;

    Intent(String code) { this.code = code; }

    @JsonValue
    public String getCode() { return code; }

    /** Parse an toàn: nhãn lạ → OUT_OF_SCOPE thay vì ném lỗi. */
    @JsonCreator
    public static Intent fromCode(String raw) {
        if (raw == null) return OUT_OF_SCOPE;
        String c = raw.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(i -> i.code.equals(c))
                .findFirst()
                .orElse(OUT_OF_SCOPE);
    }
}
