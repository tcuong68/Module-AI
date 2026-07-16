package com.roomfinder.chat.model;

/** Kết quả guardrail chống hallucination — §6.4. */
public record ValidationResult(boolean ok, String reason) {
    public static ValidationResult pass() { return new ValidationResult(true, null); }
    public static ValidationResult fail(String reason) { return new ValidationResult(false, reason); }
}
