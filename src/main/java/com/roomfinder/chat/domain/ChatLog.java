package com.roomfinder.chat.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Log mỗi lượt hội thoại phục vụ đánh giá (§8, §14). */
@Entity
@Table(name = "chat_log")
@Getter
@Setter
public class ChatLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sessionId;
    @Column(columnDefinition = "TEXT")
    private String userMessage;
    private String predictedIntent;
    private Float nluConfidence;

    @Column(columnDefinition = "json")
    private String extractedEntities;   // JSON string

    @Column(columnDefinition = "json")
    private String resultRoomIds;       // JSON string

    private String path;                // FAST | LLM | CLARIFY
    private Boolean hallucinationFlag = false;
    private Integer latencyMs;

    @Column(insertable = false, updatable = false)
    private Instant createdAt;
}
