package com.roomfinder.chat.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomfinder.chat.config.LlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Cài đặt LlmClient bằng Google Gemini (generateContent REST) — §6.2.
 * Dùng RestClient đồng bộ; timeout & lỗi được bọc thành LlmException để
 * tầng trên fallback (hệ thống không bao giờ sập vì LLM).
 */
@Component
public class GeminiClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final LlmProperties.Gemini cfg;
    private final RestClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public GeminiClient(LlmProperties props) {
        this.cfg = props.getGemini();
        this.http = RestClient.builder()
                .baseUrl(cfg.getBaseUrl())
                .requestFactory(clientRequestFactory(cfg.getTimeoutMs()))
                .build();
    }

    private static org.springframework.http.client.ClientHttpRequestFactory clientRequestFactory(int timeoutMs) {
        var f = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) Duration.ofMillis(timeoutMs).toMillis());
        f.setReadTimeout((int) Duration.ofMillis(timeoutMs).toMillis());
        return f;
    }

    @Override
    public String complete(String system, String user, double temperature) {
        if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
            throw new LlmException("GEMINI_API_KEY chưa được cấu hình");
        }

        Map<String, Object> body = Map.of(
            "system_instruction", Map.of("parts", List.of(Map.of("text", system))),
            "contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", user)))),
            "generationConfig", Map.of(
                "temperature", temperature,
                "maxOutputTokens", cfg.getMaxOutputTokens(),
                "topP", cfg.getTopP())
        );

        try {
            String uri = "/models/" + cfg.getModel() + ":generateContent?key=" + cfg.getApiKey();
            String raw = http.post()
                    .uri(uri)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return extractText(raw);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Gemini call failed: {}", e.getMessage());
            throw new LlmException("Gemini call failed: " + e.getMessage(), e);
        }
    }

    /** Bóc candidates[0].content.parts[*].text. */
    private String extractText(String raw) {
        try {
            JsonNode root = mapper.readTree(raw);
            JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                throw new LlmException("Gemini trả về rỗng: " + raw);
            }
            StringBuilder sb = new StringBuilder();
            for (JsonNode p : parts) sb.append(p.path("text").asText(""));
            String text = sb.toString().trim();
            if (text.isEmpty()) throw new LlmException("Gemini text rỗng");
            return text;
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Không parse được response Gemini", e);
        }
    }
}
