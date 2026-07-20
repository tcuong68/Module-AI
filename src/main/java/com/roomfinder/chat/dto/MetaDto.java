package com.roomfinder.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Thông tin kỹ thuật (không hiển thị cho end-user, nhưng rất hữu ích khi demo
 * trước hội đồng để chỉ ra fast-path hoạt động) — §7.1.
 */
@Getter
@Builder
@AllArgsConstructor
public class MetaDto {
    private String path;          // FAST | LLM | TEMPLATE | CLARIFY
    private boolean relaxed;
    private long latencyMs;
    private double nluConfidence;
    private boolean hallucinationDetected;
    /** GĐ3: distance | price | personalized | semantic — null nếu không áp dụng (intent khác search). */
    private String rankedBy;
}
