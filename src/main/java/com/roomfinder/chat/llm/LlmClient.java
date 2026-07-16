package com.roomfinder.chat.llm;

/**
 * Trừu tượng hóa lời gọi LLM. Cho phép thay Gemini bằng provider khác
 * (OpenAI, mock...) mà không đụng tầng NLU/NLG.
 */
public interface LlmClient {

    /**
     * Sinh văn bản từ system prompt + user message.
     * @param temperature 0.0 cho NLU (§10), 0.3 cho NLG (§6.2).
     * @throws LlmException khi gọi thất bại/không cấu hình API key.
     */
    String complete(String system, String user, double temperature);
}
