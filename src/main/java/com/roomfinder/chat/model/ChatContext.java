package com.roomfinder.chat.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Trạng thái hội thoại lưu trong Redis: key chat:session:{id}, TTL 30' (§4.1).
 * last_result_ids là NGUỒN CHÂN LÝ cho validator (§6.4) và cho "phòng thứ 2".
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatContext {

    private String sessionId;
    private Long userId;
    private Filters activeFilters = new Filters();
    private List<Long> lastResultIds = new ArrayList<>();
    private int turnCount = 0;
    private String pendingSlot;          // slot đang chờ người dùng cung cấp (§4.3)
    private String updatedAt;

    public static ChatContext empty(String sessionId) {
        ChatContext c = new ChatContext();
        c.sessionId = sessionId;
        c.updatedAt = Instant.now().toString();
        return c;
    }

    public void touch() {
        this.updatedAt = Instant.now().toString();
    }
}
