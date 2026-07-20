package com.roomfinder.chat.service;

import com.roomfinder.chat.config.RecommendationProperties;
import com.roomfinder.chat.domain.Room;
import com.roomfinder.chat.domain.RoomView;
import com.roomfinder.chat.repository.RoomRepository;
import com.roomfinder.chat.repository.RoomViewRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Kiểm thử Recommendation Engine (§12.2, GĐ3) — feature vector + rank thuần logic. */
class RecommendationServiceTest {

    private final RoomRepository roomRepo = mock(RoomRepository.class);
    private final RoomViewRepository viewRepo = mock(RoomViewRepository.class);
    private final RecommendationProperties props = new RecommendationProperties();
    private final RecommendationService service = new RecommendationService(roomRepo, viewRepo, props);

    private Room room(long id, long price, double area, double lat, double lng, boolean ac, boolean parking) {
        Room r = new Room();
        r.setId(id);
        r.setPrice(price);
        r.setArea(BigDecimal.valueOf(area));
        r.setLatitude(BigDecimal.valueOf(lat));
        r.setLongitude(BigDecimal.valueOf(lng));
        r.setHasAirConditioner(ac);
        r.setHasParking(parking);
        r.setHasWifi(true);
        r.setHasWashingMachine(false);
        r.setIsPrivateBathroom(true);
        return r;
    }

    @Test
    void returnsNullWhenNoUserId() {
        List<Room> candidates = List.of(room(1, 2_000_000, 20, 21.0, 105.8, true, true),
                room(2, 3_000_000, 25, 21.0, 105.8, false, false));
        assertNull(service.rank(null, candidates));
    }

    @Test
    void returnsNullWhenNotEnoughViewHistory() {
        when(viewRepo.findByUserIdOrderByViewedAtDesc(42L)).thenReturn(List.of());
        List<Room> candidates = List.of(room(1, 2_000_000, 20, 21.0, 105.8, true, true),
                room(2, 3_000_000, 25, 21.0, 105.8, false, false));
        assertNull(service.rank(42L, candidates));
    }

    @Test
    void ranksCandidatesSimilarToViewedRoomsFirst() {
        Room cheapAc = room(1, 2_000_000, 18, 21.00, 105.80, true, true);   // đã xem — rẻ, có điều hòa
        Room expensiveNoAc = room(2, 6_000_000, 40, 21.05, 105.85, false, false);
        Room cheapAcCandidate = room(3, 2_100_000, 19, 21.00, 105.80, true, true);   // giống phòng đã xem
        Room expensiveCandidate = room(4, 5_800_000, 38, 21.05, 105.85, false, false); // giống phòng còn lại

        List<Room> allRooms = List.of(cheapAc, expensiveNoAc, cheapAcCandidate, expensiveCandidate);
        when(roomRepo.findAll()).thenReturn(allRooms);

        RoomView v1 = new RoomView(42L, 1L);
        v1.setViewedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        RoomView v2 = new RoomView(42L, 1L);
        v2.setViewedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        when(viewRepo.findByUserIdOrderByViewedAtDesc(42L)).thenReturn(List.of(v1, v2));

        List<Room> candidates = List.of(expensiveCandidate, cheapAcCandidate); // cố ý đảo thứ tự gốc
        List<Room> ranked = service.rank(42L, candidates);

        assertEquals(List.of(3L, 4L), ranked.stream().map(Room::getId).toList());
    }
}
