package com.roomfinder.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình geocoding fallback (TODO.md "Tầng 2") — khối `roomfinder.geocoding`.
 * Bounding box mặc định phủ Hà Nội (nội + ngoại thành) — dùng để loại kết quả
 * geocode sai (câu cụt/sai chính tả bị nhà cung cấp cố trả về "một tọa độ
 * nào đó" thay vì báo lỗi).
 */
@ConfigurationProperties(prefix = "roomfinder.geocoding")
public class GeocodingProperties {

    private boolean enabled = true;
    private String baseUrl = "https://nominatim.openstreetmap.org";
    private int timeoutMs = 3000;
    /** Nominatim yêu cầu User-Agent định danh rõ ràng, chặn UA mặc định của HTTP client. */
    private String userAgent = "RoomFinderChatbot-Thesis/1.0";
    private double minLat = 20.55;
    private double maxLat = 21.40;
    private double minLon = 105.25;
    private double maxLon = 106.05;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String v) { this.baseUrl = v; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int v) { this.timeoutMs = v; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String v) { this.userAgent = v; }
    public double getMinLat() { return minLat; }
    public void setMinLat(double v) { this.minLat = v; }
    public double getMaxLat() { return maxLat; }
    public void setMaxLat(double v) { this.maxLat = v; }
    public double getMinLon() { return minLon; }
    public void setMinLon(double v) { this.minLon = v; }
    public double getMaxLon() { return maxLon; }
    public void setMaxLon(double v) { this.maxLon = v; }
}
