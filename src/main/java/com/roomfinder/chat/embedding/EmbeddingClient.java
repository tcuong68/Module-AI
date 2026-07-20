package com.roomfinder.chat.embedding;

/**
 * Embedding văn bản cho semantic rerank (GĐ3, SPEC §12.1).
 * Trả {@code null} khi lỗi/tắt tính năng — tầng gọi (SemanticRerankService)
 * bỏ qua rerank, KHÔNG làm sập luồng chat, giống mọi client tùy chọn khác
 * (GeocodingClient, PhoBertNluServiceImpl).
 */
public interface EmbeddingClient {
    float[] embed(String text);
}
