package com.roomfinder.chat.model;

/**
 * Kết quả tầng NLG.
 * @param reply câu trả lời (đã qua guardrail) hoặc null nếu thất bại.
 * @param valid true nếu LLM trả lời và VƯỢT guardrail.
 * @param hallucinationDetected có bắt được ít nhất 1 lần bịa (để tính metric §14.2).
 */
public record NlgOutcome(String reply, boolean valid, boolean hallucinationDetected) {
}
