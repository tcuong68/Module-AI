package com.roomfinder.chat.geocoding;

import com.roomfinder.chat.config.GeocodingProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Kiểm thử phần logic thuần (parse JSON + validate bounding box) — TODO.md "Tầng 2". */
class NominatimGeocodingClientTest {

    private final NominatimGeocodingClient client = new NominatimGeocodingClient(new GeocodingProperties());

    @Test
    void parsesValidResultWithinHanoi() {
        String raw = """
                [{"lat":"21.0031","lon":"105.8390","display_name":"Đại học Thủy Lợi, Hà Nội"}]""";
        GeocodeResult r = client.parse(raw, "đại học thủy lợi");
        assertEquals(21.0031, r.latitude());
        assertEquals(105.8390, r.longitude());
    }

    @Test
    void rejectsResultOutsideBoundingBox() {
        // Tọa độ TP.HCM — geocode nhầm/quá xa Hà Nội phải bị chặn.
        String raw = """
                [{"lat":"10.7769","lon":"106.7009","display_name":"TP.HCM"}]""";
        assertNull(client.parse(raw, "câu hỏi mơ hồ"));
    }

    @Test
    void returnsNullOnEmptyResults() {
        assertNull(client.parse("[]", "địa danh không tồn tại"));
    }

    @Test
    void withinBoundsAcceptsHanoiCoordinates() {
        assertEquals(true, client.withinBounds(21.0285, 105.8542)); // Hồ Gươm
    }

    @Test
    void withinBoundsRejectsFarAwayCoordinates() {
        assertEquals(false, client.withinBounds(10.7769, 106.7009)); // TP.HCM
    }
}
