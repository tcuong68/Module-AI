package com.roomfinder.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Cấu hình nhà cung cấp LLM — khối `roomfinder.llm`.
 */
@ConfigurationProperties(prefix = "roomfinder.llm")
public class LlmProperties {

    private String provider = "gemini";
    @NestedConfigurationProperty
    private Gemini gemini = new Gemini();

    public static class Gemini {
        private String apiKey = "";
        private String model = "gemini-1.5-flash";
        private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        private double temperatureNlu = 0.0;
        private double temperatureNlg = 0.3;
        private int maxOutputTokens = 300;
        private double topP = 0.9;
        private int timeoutMs = 8000;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String v) { this.apiKey = v; }
        public String getModel() { return model; }
        public void setModel(String v) { this.model = v; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String v) { this.baseUrl = v; }
        public double getTemperatureNlu() { return temperatureNlu; }
        public void setTemperatureNlu(double v) { this.temperatureNlu = v; }
        public double getTemperatureNlg() { return temperatureNlg; }
        public void setTemperatureNlg(double v) { this.temperatureNlg = v; }
        public int getMaxOutputTokens() { return maxOutputTokens; }
        public void setMaxOutputTokens(int v) { this.maxOutputTokens = v; }
        public double getTopP() { return topP; }
        public void setTopP(double v) { this.topP = v; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int v) { this.timeoutMs = v; }
    }

    public String getProvider() { return provider; }
    public void setProvider(String v) { this.provider = v; }
    public Gemini getGemini() { return gemini; }
    public void setGemini(Gemini v) { this.gemini = v; }
}
