package com.roomfinder.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình module chatbot — ánh xạ từ khối `roomfinder.chat` trong application.yml.
 */
@ConfigurationProperties(prefix = "roomfinder.chat")
public class ChatProperties {

    private int contextTtlMinutes = 30;
    private int topK = 5;
    private int defaultRadiusM = 1500;
    private int cheaperStepPercent = 20;
    private FastPath fastPath = new FastPath();

    public static class FastPath {
        private double confidenceThreshold = 0.90;
        public double getConfidenceThreshold() { return confidenceThreshold; }
        public void setConfidenceThreshold(double v) { this.confidenceThreshold = v; }
    }

    public int getContextTtlMinutes() { return contextTtlMinutes; }
    public void setContextTtlMinutes(int v) { this.contextTtlMinutes = v; }
    public int getTopK() { return topK; }
    public void setTopK(int v) { this.topK = v; }
    public int getDefaultRadiusM() { return defaultRadiusM; }
    public void setDefaultRadiusM(int v) { this.defaultRadiusM = v; }
    public int getCheaperStepPercent() { return cheaperStepPercent; }
    public void setCheaperStepPercent(int v) { this.cheaperStepPercent = v; }
    public FastPath getFastPath() { return fastPath; }
    public void setFastPath(FastPath v) { this.fastPath = v; }
}
