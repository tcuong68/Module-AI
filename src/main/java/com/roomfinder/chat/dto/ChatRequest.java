package com.roomfinder.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Body của POST /api/v1/chat — §7.1. */
@Getter
@Setter
public class ChatRequest {
    private String sessionId;
    @NotBlank
    private String message;
    private Long userId;   // tùy chọn, phục vụ cá nhân hóa GĐ3
}
