package com.roomfinder.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomfinder.chat.config.SemanticProperties;
import com.roomfinder.chat.domain.Room;
import com.roomfinder.chat.embedding.EmbeddingClient;
import com.roomfinder.chat.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Semantic rerank — SPEC §12.1 (GĐ3). KHÔNG dùng Elasticsearch (xem README §8):
 * ở quy mô đồ án (≤100 phòng), so cosine brute-force trong Java trên vector đã
 * cache nhanh hơn round-trip tới 1 service ES riêng.
 *
 * Nguyên tắc vàng (§5.1) vẫn giữ nguyên: hàm này chỉ RERANK candidate ĐÃ được
 * lọc cứng bằng SQL (giá/diện tích/tiện ích) — không bao giờ dùng để filter.
 */
@Service
public class SemanticRerankService {

    private static final Logger log = LoggerFactory.getLogger(SemanticRerankService.class);

    private final EmbeddingClient embeddingClient;
    private final RoomRepository roomRepo;
    private final SemanticProperties props;
    private final ObjectMapper mapper;

    public SemanticRerankService(EmbeddingClient embeddingClient, RoomRepository roomRepo,
                                  SemanticProperties props, ObjectMapper mapper) {
        this.embeddingClient = embeddingClient;
        this.roomRepo = roomRepo;
        this.props = props;
        this.mapper = mapper;
    }

    /**
     * Sắp lại {@code candidates} theo độ tương đồng ngữ nghĩa giữa câu hỏi gốc
     * và mô tả phòng. Trả {@code null} khi không rerank được (tắt tính năng,
     * embedding lỗi) — tầng gọi giữ nguyên thứ tự gốc.
     */
    public List<Room> rerank(String message, List<Room> candidates) {
        if (!props.isEnabled() || candidates == null || candidates.size() < 2) return null;
        float[] queryVec = embeddingClient.embed(message);
        if (queryVec == null) return null;

        candidates.forEach(this::ensureVector);
        return candidates.stream()
                .sorted(Comparator.comparingDouble((Room r) -> -scoreOf(r, queryVec)))
                .toList();
    }

    /** Tính + cache description_vector nếu chưa có (lazy, chỉ 1 lần/phòng). */
    private void ensureVector(Room r) {
        if (r.getDescriptionVector() != null) return;
        float[] v = embeddingClient.embed(r.getDescription());
        if (v == null) return;
        try {
            r.setDescriptionVector(mapper.writeValueAsString(v));
            roomRepo.save(r);
        } catch (Exception e) {
            log.warn("Lưu description_vector lỗi cho room {}: {}", r.getId(), e.getMessage());
        }
    }

    private double scoreOf(Room r, float[] queryVec) {
        float[] v = parse(r.getDescriptionVector());
        return v == null ? -1.0 : cosine(queryVec, v);
    }

    private float[] parse(String json) {
        if (json == null) return null;
        try {
            return mapper.readValue(json, float[].class);
        } catch (Exception e) {
            return null;
        }
    }

    private static double cosine(float[] a, float[] b) {
        if (a.length != b.length) return -1.0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return -1.0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
