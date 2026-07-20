package com.roomfinder.chat.geocoding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomfinder.chat.config.GeocodingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Geocoding fallback bằng OSM Nominatim (miễn phí) — TODO.md "Tầng 2".
 *
 * Chỉ được gọi khi bảng {@code poi} nội bộ miss (xem
 * {@code RetrievalService.resolvePoi}); kết quả sau đó được cache lại vào
 * DB nên tần suất gọi thực tế rất thấp — đủ để bỏ qua rate limiter riêng dù
 * Nominatim khuyến nghị ≤1 req/s cho usage cá nhân.
 */
@Component
public class NominatimGeocodingClient implements GeocodingClient {

    private static final Logger log = LoggerFactory.getLogger(NominatimGeocodingClient.class);

    private final GeocodingProperties props;
    private final RestClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public NominatimGeocodingClient(GeocodingProperties props) {
        this.props = props;
        this.http = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(requestFactory(props.getTimeoutMs()))
                .defaultHeader("User-Agent", props.getUserAgent())
                .build();
    }

    private static ClientHttpRequestFactory requestFactory(int timeoutMs) {
        var f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(timeoutMs);
        f.setReadTimeout(timeoutMs);
        return f;
    }

    @Override
    public GeocodeResult geocode(String query) {
        if (!props.isEnabled()) return null;
        try {
            String raw = http.get()
                    .uri(uriBuilder -> uriBuilder.path("/search")
                            .queryParam("q", query)
                            .queryParam("format", "json")
                            .queryParam("limit", 1)
                            .queryParam("countrycodes", "vn")
                            // viewbox=<west lon>,<north lat>,<east lon>,<south lat> — bias kết quả về Hà Nội
                            .queryParam("viewbox", props.getMinLon() + "," + props.getMaxLat() + ","
                                    + props.getMaxLon() + "," + props.getMinLat())
                            .queryParam("bounded", 1)
                            .build())
                    .retrieve()
                    .body(String.class);
            return parse(raw, query);
        } catch (Exception e) {
            log.warn("Geocode '{}' thất bại: {}", query, e.getMessage());
            return null;
        }
    }

    /** package-private để unit test không cần gọi mạng thật. */
    GeocodeResult parse(String raw, String query) {
        try {
            JsonNode arr = mapper.readTree(raw);
            if (!arr.isArray() || arr.isEmpty()) {
                log.info("Nominatim không có kết quả cho '{}' (có thể do độ phủ dữ liệu OSM hạn chế "
                        + "với địa danh nhỏ/ít người biết — cân nhắc Goong.io/VietMap nếu gặp thường xuyên)", query);
                return null;
            }
            JsonNode first = arr.get(0);
            double lat = first.path("lat").asDouble(Double.NaN);
            double lon = first.path("lon").asDouble(Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lon)) return null;

            if (!withinBounds(lat, lon)) {
                log.warn("Geocode '{}' ra tọa độ ngoài phạm vi hợp lệ ({}, {}) — bỏ qua", query, lat, lon);
                return null;
            }
            return new GeocodeResult(lat, lon, first.path("display_name").asText(query));
        } catch (Exception e) {
            log.warn("Không parse được response Nominatim cho '{}': {}", query, e.getMessage());
            return null;
        }
    }

    /** Chặn cứng: câu cụt/sai chính tả không được phép biến thành tọa độ vô nghĩa. */
    boolean withinBounds(double lat, double lon) {
        return lat >= props.getMinLat() && lat <= props.getMaxLat()
                && lon >= props.getMinLon() && lon <= props.getMaxLon();
    }
}
