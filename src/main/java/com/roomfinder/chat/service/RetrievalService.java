package com.roomfinder.chat.service;

import com.roomfinder.chat.config.ChatProperties;
import com.roomfinder.chat.domain.Poi;
import com.roomfinder.chat.domain.Room;
import com.roomfinder.chat.model.Filters;
import com.roomfinder.chat.model.RetrievalResult;
import com.roomfinder.chat.repository.PoiRepository;
import com.roomfinder.chat.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;

/**
 * Tầng Retrieval — §5. Filter cứng bằng SQL (giá/diện tích/bán kính),
 * hỗ trợ truy vấn "gần POI" bằng ST_Distance_Sphere, và chiến lược nới lỏng
 * khi 0 kết quả (§5.3) — không bao giờ trả "không tìm thấy" rồi dừng.
 */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final RoomRepository roomRepo;
    private final PoiRepository poiRepo;
    private final int topK;
    private final int defaultRadius;

    public RetrievalService(RoomRepository roomRepo, PoiRepository poiRepo, ChatProperties props) {
        this.roomRepo = roomRepo;
        this.poiRepo = poiRepo;
        this.topK = props.getTopK();
        this.defaultRadius = props.getDefaultRadiusM();
    }

    public RetrievalResult search(Filters f) {
        Poi poi = f.getPoi() != null ? resolvePoi(f.getPoi()) : null;
        double radius = f.getRadiusM() != null ? f.getRadiusM() : defaultRadius;

        // Lượt truy vấn gốc (filter cứng)
        List<Room> base = runQuery(f, poi, radius);
        if (!base.isEmpty()) {
            attachDistance(base, poi);
            return RetrievalResult.of(base);
        }

        // --- Nới lỏng (§5.3) ---
        // 1) Nới price_max thêm 15%
        if (f.getPriceMax() != null) {
            Filters r = f.copy();
            long newMax = Math.round(f.getPriceMax() * 1.15);
            r.setPriceMax(newMax);
            List<Room> l = runQuery(r, poi, radius);
            if (!l.isEmpty()) {
                attachDistance(l, poi);
                return RetrievalResult.relaxed(l, "nới giá tối đa lên " + formatVnd(newMax));
            }
        }
        // 2) Nới bán kính gấp đôi (chỉ khi tìm theo POI)
        if (poi != null) {
            List<Room> l = runQuery(f, poi, radius * 2);
            if (!l.isEmpty()) {
                attachDistance(l, poi);
                return RetrievalResult.relaxed(l, "mở rộng bán kính lên " + (int) (radius * 2) + "m");
            }
        }
        // 3) Bỏ yêu cầu tiện ích
        if (f.getUtilities() != null && !f.getUtilities().isEmpty()) {
            Filters r = f.copy();
            r.getUtilities().clear();
            List<Room> l = runQuery(r, poi, poi != null ? radius * 2 : radius);
            if (!l.isEmpty()) {
                attachDistance(l, poi);
                return RetrievalResult.relaxed(l, "bỏ bớt yêu cầu tiện ích");
            }
        }
        // 4) Fallback: vài phòng gần nhất/cùng khu vực với điều kiện tối thiểu
        List<Room> fallback;
        String note;
        if (poi != null) {
            fallback = roomRepo.searchByGeo(null, null, null, null, null, null, null, null,
                    poi.getLatitude().doubleValue(), poi.getLongitude().doubleValue(), 50_000, 3);
            attachDistance(fallback, poi);
            note = "gợi ý phòng gần " + f.getPoi() + " nhất (đã bỏ các ràng buộc khác)";
        } else {
            fallback = roomRepo.searchByFilters(null, null, f.getLocation(), null, null, null, null, null, null, 3);
            note = "gợi ý vài phòng cùng khu vực (đã bỏ các ràng buộc khác)";
        }
        return RetrievalResult.relaxed(fallback, note);
    }

    // --- Helpers ---------------------------------------------------------

    private List<Room> runQuery(Filters f, Poi poi, double radius) {
        Boolean hasAc = f.hasUtility("air_conditioner") ? Boolean.TRUE : null;
        Boolean hasParking = f.hasUtility("parking") ? Boolean.TRUE : null;
        Boolean hasWifi = f.hasUtility("wifi") ? Boolean.TRUE : null;
        Boolean hasWashing = f.hasUtility("washing_machine") ? Boolean.TRUE : null;

        if (poi != null) {
            return roomRepo.searchByGeo(f.getPriceMin(), f.getPriceMax(), f.getAreaMin(),
                    hasAc, hasParking, hasWifi, hasWashing, f.getRoomType(),
                    poi.getLatitude().doubleValue(), poi.getLongitude().doubleValue(), radius, topK);
        }
        return roomRepo.searchByFilters(f.getPriceMin(), f.getPriceMax(), f.getLocation(),
                f.getAreaMin(), hasAc, hasParking, hasWifi, hasWashing, f.getRoomType(), topK);
    }

    /** Khớp span POI với name/aliases của bảng poi (không dấu, contains). */
    private Poi resolvePoi(String span) {
        String needle = strip(span);
        return poiRepo.findAll().stream()
                .filter(p -> matchesPoi(p, needle))
                .findFirst()
                .orElse(null);
    }

    private boolean matchesPoi(Poi p, String needle) {
        if (strip(p.getName()).contains(needle) || needle.contains(strip(p.getName()))) return true;
        if (p.getAliases() == null) return false;
        for (String a : p.getAliases()) {
            String sa = strip(a);
            if (sa.contains(needle) || needle.contains(sa)) return true;
        }
        return false;
    }

    /** Gán distance_m (Haversine) khi tìm theo POI để hiển thị RoomCard. */
    private void attachDistance(List<Room> rooms, Poi poi) {
        if (poi == null) return;
        double lat0 = poi.getLatitude().doubleValue();
        double lon0 = poi.getLongitude().doubleValue();
        for (Room r : rooms) {
            if (r.getLatitude() != null && r.getLongitude() != null) {
                r.setDistanceM(haversine(lat0, lon0,
                        r.getLatitude().doubleValue(), r.getLongitude().doubleValue()));
            }
        }
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double R = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static String strip(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.toLowerCase().trim(), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "").replace("đ", "d");
    }

    private static String formatVnd(long v) {
        if (v % 1_000_000 == 0) return (v / 1_000_000) + " triệu";
        return String.format("%,d", v).replace(',', '.') + "đ";
    }
}
