package com.roomfinder.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình semantic rerank (GĐ3, SPEC §12.1) — khối `roomfinder.semantic`.
 * Dùng chung base URL với NLU PhoBERT (`roomfinder.nlu.url`) vì endpoint
 * {@code /embed} nằm trên cùng nlu-service (FastAPI) — không lặp lại cấu hình URL.
 */
@ConfigurationProperties(prefix = "roomfinder.semantic")
public class SemanticProperties {

    private boolean enabled = true;
    /** Riêng, thường lâu hơn timeout NLU (300ms) vì SBERT nặng hơn phân loại intent. */
    private int embedTimeoutMs = 3000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public int getEmbedTimeoutMs() { return embedTimeoutMs; }
    public void setEmbedTimeoutMs(int v) { this.embedTimeoutMs = v; }
}
