package com.roomfinder.chat.geocoding;

/**
 * Geocode 1 địa danh (POI) tiếng Việt thành tọa độ — dùng khi bảng `poi`
 * nội bộ không có mốc người dùng nhắc tới (TODO.md "Tầng 2").
 *
 * Trả {@code null} khi không tìm thấy / lỗi mạng / tọa độ ngoài phạm vi hợp
 * lệ — KHÔNG throw, để tầng gọi (RetrievalService) chỉ cần kiểm tra null,
 * giống hệt cách resolvePoi() đã xử lý "miss" từ trước tới giờ.
 */
public interface GeocodingClient {
    GeocodeResult geocode(String query);
}
