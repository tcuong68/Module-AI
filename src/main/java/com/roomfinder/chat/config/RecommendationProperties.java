package com.roomfinder.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình Recommendation Engine (GĐ3, SPEC §12.2) — khối `roomfinder.recommendation`.
 */
@ConfigurationProperties(prefix = "roomfinder.recommendation")
public class RecommendationProperties {

    private boolean enabled = true;
    /** Cần ít nhất N lượt xem mới personalize — quá ít thì hồ sơ không đáng tin. */
    private int minViews = 2;
    /** Trọng số lượt xem giảm còn 1 nửa sau mỗi N ngày (time-decay). */
    private double halfLifeDays = 30;
    /** Lấy pool ứng viên rộng hơn topK bao nhiêu lần khi có thể personalize. */
    private int candidatePoolMultiplier = 4;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public int getMinViews() { return minViews; }
    public void setMinViews(int v) { this.minViews = v; }
    public double getHalfLifeDays() { return halfLifeDays; }
    public void setHalfLifeDays(double v) { this.halfLifeDays = v; }
    public int getCandidatePoolMultiplier() { return candidatePoolMultiplier; }
    public void setCandidatePoolMultiplier(int v) { this.candidatePoolMultiplier = v; }
}
