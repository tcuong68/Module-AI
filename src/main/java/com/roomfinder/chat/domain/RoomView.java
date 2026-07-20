package com.roomfinder.chat.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Lượt "xem phòng" — GĐ3 Recommendation Engine (SPEC §12.2).
 * Ghi khi người dùng hỏi cụ thể về 1 phòng (room_detail/compare_rooms/
 * calculate_cost) — xem RetrievalService/ChatOrchestrator.
 */
@Entity
@Table(name = "room_view")
@Getter
@Setter
@NoArgsConstructor
public class RoomView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long roomId;
    private Instant viewedAt;

    public RoomView(Long userId, Long roomId) {
        this.userId = userId;
        this.roomId = roomId;
        this.viewedAt = Instant.now();
    }
}
