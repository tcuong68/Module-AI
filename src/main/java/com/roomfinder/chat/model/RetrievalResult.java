package com.roomfinder.chat.model;

import com.roomfinder.chat.domain.Room;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** Kết quả tầng retrieval kèm cờ nới lỏng (§5.3). */
@Getter
@AllArgsConstructor
public class RetrievalResult {
    private final List<Room> rooms;
    private final boolean relaxed;
    private final String relaxationNote;   // mô tả đã nới gì, null nếu không nới

    public static RetrievalResult of(List<Room> rooms) {
        return new RetrievalResult(rooms, false, null);
    }
    public static RetrievalResult relaxed(List<Room> rooms, String note) {
        return new RetrievalResult(rooms, true, note);
    }
    public boolean isEmpty() { return rooms == null || rooms.isEmpty(); }
}
