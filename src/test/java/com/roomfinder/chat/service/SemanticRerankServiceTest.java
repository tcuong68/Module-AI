package com.roomfinder.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomfinder.chat.config.SemanticProperties;
import com.roomfinder.chat.domain.Room;
import com.roomfinder.chat.embedding.EmbeddingClient;
import com.roomfinder.chat.repository.RoomRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Kiểm thử semantic rerank (§12.1, GĐ3) — thuần logic, EmbeddingClient giả lập. */
class SemanticRerankServiceTest {

    private final EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
    private final RoomRepository roomRepo = mock(RoomRepository.class);
    private final SemanticProperties props = new SemanticProperties();
    private final SemanticRerankService service =
            new SemanticRerankService(embeddingClient, roomRepo, props, new ObjectMapper());

    private Room room(long id, String desc) {
        Room r = new Room();
        r.setId(id);
        r.setDescription(desc);
        return r;
    }

    @Test
    void returnsNullWhenEmbeddingFails() {
        when(embeddingClient.embed("phòng yên tĩnh")).thenReturn(null);
        assertNull(service.rerank("phòng yên tĩnh", List.of(room(1, "a"), room(2, "b"))));
    }

    @Test
    void ranksRoomWithMatchingDescriptionFirst() {
        Room quiet = room(1, "phòng yên tĩnh thoáng mát");
        Room busy = room(2, "khu sôi động gần chợ đêm");

        when(embeddingClient.embed("tìm phòng yên tĩnh")).thenReturn(new float[]{1, 0, 0});
        when(embeddingClient.embed("phòng yên tĩnh thoáng mát")).thenReturn(new float[]{0.9f, 0.1f, 0});
        when(embeddingClient.embed("khu sôi động gần chợ đêm")).thenReturn(new float[]{0, 0, 1});

        List<Room> ranked = service.rerank("tìm phòng yên tĩnh", List.of(busy, quiet));

        assertEquals(List.of(1L, 2L), ranked.stream().map(Room::getId).toList());
    }
}
