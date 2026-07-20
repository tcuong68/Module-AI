package com.roomfinder.chat.embedding;

import com.roomfinder.chat.config.NluProperties;
import com.roomfinder.chat.config.SemanticProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Gọi endpoint {@code POST /embed} trên nlu-service (cùng service với NLU
 * PhoBERT — xem {@code nlu-service/app.py}) để lấy vector embedding tiếng Việt.
 */
@Component
public class NluEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(NluEmbeddingClient.class);

    private final RestClient http;
    private final SemanticProperties props;

    public NluEmbeddingClient(NluProperties nluProps, SemanticProperties props) {
        this.props = props;
        var f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(props.getEmbedTimeoutMs());
        f.setReadTimeout(props.getEmbedTimeoutMs());
        this.http = RestClient.builder().baseUrl(nluProps.getUrl()).requestFactory(f).build();
    }

    @Override
    public float[] embed(String text) {
        if (!props.isEnabled() || text == null || text.isBlank()) return null;
        try {
            EmbedResponse r = http.post()
                    .uri("/embed")
                    .header("Content-Type", "application/json")
                    .body(Map.of("text", text))
                    .retrieve()
                    .body(EmbedResponse.class);
            if (r == null || r.vector() == null || r.vector().isEmpty()) return null;
            float[] out = new float[r.vector().size()];
            for (int i = 0; i < out.length; i++) out[i] = r.vector().get(i).floatValue();
            return out;
        } catch (Exception e) {
            log.warn("Embedding call thất bại: {}", e.getMessage());
            return null;
        }
    }

    record EmbedResponse(List<Double> vector) {}
}
