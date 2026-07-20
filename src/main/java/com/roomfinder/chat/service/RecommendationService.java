package com.roomfinder.chat.service;

import com.roomfinder.chat.config.RecommendationProperties;
import com.roomfinder.chat.domain.Room;
import com.roomfinder.chat.domain.RoomView;
import com.roomfinder.chat.repository.RoomRepository;
import com.roomfinder.chat.repository.RoomViewRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Recommendation Engine — Content-Based Filtering + KNN (SPEC §12.2, GĐ3).
 *
 * Vector đặc trưng mỗi phòng: [price_norm, area_norm, lat_norm, lng_norm,
 * has_ac, has_parking, has_wifi, has_washing_machine, is_private_bathroom]
 * (chuẩn hóa min-max theo TOÀN CATALOG, không phải riêng candidate pool —
 * để hồ sơ người dùng và candidate luôn cùng 1 thang đo).
 *
 * Hồ sơ người dùng = trung bình có trọng số (giảm dần theo thời gian, half-life
 * cấu hình được) các phòng trong lịch sử xem (`room_view`). Gợi ý = sắp xếp
 * candidate theo cosine similarity với hồ sơ, giảm dần.
 */
@Service
public class RecommendationService {

    private final RoomRepository roomRepo;
    private final RoomViewRepository roomViewRepo;
    private final RecommendationProperties props;

    public RecommendationService(RoomRepository roomRepo, RoomViewRepository roomViewRepo,
                                  RecommendationProperties props) {
        this.roomRepo = roomRepo;
        this.roomViewRepo = roomViewRepo;
        this.props = props;
    }

    /**
     * Sắp lại {@code candidates} theo hồ sơ cá nhân của {@code userId}.
     * Trả {@code null} khi KHÔNG personalize được (tắt tính năng, không có
     * userId, hoặc chưa đủ lịch sử xem) — tầng gọi giữ nguyên thứ tự gốc.
     */
    public List<Room> rank(Long userId, List<Room> candidates) {
        if (!props.isEnabled() || userId == null || candidates == null || candidates.size() < 2) {
            return null;
        }
        List<RoomView> views = roomViewRepo.findByUserIdOrderByViewedAtDesc(userId);
        if (views.size() < props.getMinViews()) {
            return null;
        }

        List<Room> allRooms = roomRepo.findAll();
        FeatureStats stats = FeatureStats.compute(allRooms);
        Map<Long, Room> byId = allRooms.stream().collect(Collectors.toMap(Room::getId, Function.identity(), (a, b) -> a));

        double[] profile = buildProfile(views, byId, stats);
        if (profile == null) {
            return null; // không phòng nào trong lịch sử còn tồn tại (hiếm khi xảy ra)
        }

        return candidates.stream()
                .sorted(Comparator.comparingDouble((Room r) -> -cosine(profile, vector(r, stats))))
                .toList();
    }

    private double[] buildProfile(List<RoomView> views, Map<Long, Room> byId, FeatureStats stats) {
        Instant now = Instant.now();
        double[] sum = new double[9];
        double totalWeight = 0;
        boolean any = false;

        for (RoomView v : views) {
            Room r = byId.get(v.getRoomId());
            if (r == null) continue;
            any = true;
            double ageDays = Duration.between(v.getViewedAt(), now).toSeconds() / 86400.0;
            double weight = Math.pow(0.5, Math.max(ageDays, 0) / props.getHalfLifeDays());
            double[] vec = vector(r, stats);
            for (int i = 0; i < vec.length; i++) sum[i] += vec[i] * weight;
            totalWeight += weight;
        }
        if (!any || totalWeight <= 0) return null;
        for (int i = 0; i < sum.length; i++) sum[i] /= totalWeight;
        return sum;
    }

    static double[] vector(Room r, FeatureStats s) {
        return new double[] {
                norm(r.getPrice() == null ? 0 : r.getPrice(), s.minPrice, s.maxPrice),
                norm(r.getArea() == null ? 0 : r.getArea().doubleValue(), s.minArea, s.maxArea),
                norm(r.getLatitude() == null ? 0 : r.getLatitude().doubleValue(), s.minLat, s.maxLat),
                norm(r.getLongitude() == null ? 0 : r.getLongitude().doubleValue(), s.minLng, s.maxLng),
                b(r.getHasAirConditioner()),
                b(r.getHasParking()),
                b(r.getHasWifi()),
                b(r.getHasWashingMachine()),
                b(r.getIsPrivateBathroom()),
        };
    }

    private static double norm(double v, double min, double max) {
        return max > min ? (v - min) / (max - min) : 0.5;
    }

    private static double b(Boolean v) {
        return Boolean.TRUE.equals(v) ? 1.0 : 0.0;
    }

    static double cosine(double[] a, double[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    /** Thống kê min/max toàn catalog — dùng để chuẩn hóa các trường số. */
    static final class FeatureStats {
        double minPrice, maxPrice, minArea, maxArea, minLat, maxLat, minLng, maxLng;

        static FeatureStats compute(List<Room> rooms) {
            FeatureStats s = new FeatureStats();
            s.minPrice = rooms.stream().mapToDouble(r -> r.getPrice() == null ? 0 : r.getPrice()).min().orElse(0);
            s.maxPrice = rooms.stream().mapToDouble(r -> r.getPrice() == null ? 0 : r.getPrice()).max().orElse(1);
            s.minArea = rooms.stream().mapToDouble(r -> r.getArea() == null ? 0 : r.getArea().doubleValue()).min().orElse(0);
            s.maxArea = rooms.stream().mapToDouble(r -> r.getArea() == null ? 0 : r.getArea().doubleValue()).max().orElse(1);
            s.minLat = rooms.stream().mapToDouble(r -> r.getLatitude() == null ? 0 : r.getLatitude().doubleValue()).min().orElse(0);
            s.maxLat = rooms.stream().mapToDouble(r -> r.getLatitude() == null ? 0 : r.getLatitude().doubleValue()).max().orElse(1);
            s.minLng = rooms.stream().mapToDouble(r -> r.getLongitude() == null ? 0 : r.getLongitude().doubleValue()).min().orElse(0);
            s.maxLng = rooms.stream().mapToDouble(r -> r.getLongitude() == null ? 0 : r.getLongitude().doubleValue()).max().orElse(1);
            return s;
        }
    }
}
