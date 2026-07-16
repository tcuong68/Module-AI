package com.roomfinder.chat.llm;

/** Lỗi khi gọi LLM — dùng để kích hoạt fallback (§6.4, §11 bước 2.3). */
public class LlmException extends RuntimeException {
    public LlmException(String message) { super(message); }
    public LlmException(String message, Throwable cause) { super(message, cause); }
}
