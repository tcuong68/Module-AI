package com.roomfinder.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Module Chatbot AI hỗ trợ tìm phòng trọ — Giai đoạn 1 (MVP).
 * Luồng: NLU (LLM JSON) → Normalizer → Context (Redis) → Slot Checker
 *        → Retrieval (MySQL geo) → Fast-path / LLM-path + Guardrail.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ChatApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }
}
