package com.roomfinder.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình NLU service PhoBERT (GĐ2, §11 bước 2.4) — khối `roomfinder.nlu`.
 * Timeout mặc định 300ms theo SPEC: NLU cục bộ phải nhanh, chậm hơn thì
 * fallback LLM còn rẻ hơn là bắt người dùng đợi.
 */
@ConfigurationProperties(prefix = "roomfinder.nlu")
public class NluProperties {

    private boolean enabled = true;
    private String url = "http://localhost:8000";
    private int timeoutMs = 300;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public String getUrl() { return url; }
    public void setUrl(String v) { this.url = v; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int v) { this.timeoutMs = v; }
}
