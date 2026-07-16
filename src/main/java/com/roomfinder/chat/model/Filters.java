package com.roomfinder.chat.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Bộ tiêu chí tìm phòng — vừa là "entities" do NLU trả về (§3.4),
 * vừa là "active_filters" lưu trong context (§4.1). Dùng chung một class
 * để phép MERGE/OVERRIDE (§4.2) chỉ cần một chỗ.
 *
 * Quy ước: null = người dùng KHÔNG nhắc đến (bỏ qua khi build query).
 */
@Getter
@Setter
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Filters {

    private Long priceMin;
    private Long priceMax;
    private String location;
    private String poi;
    private Integer radiusM;
    private Double areaMin;
    private List<String> utilities = new ArrayList<>();   // keys: air_conditioner, parking, wifi, washing_machine
    private String roomType;
    private String datetime;                              // ISO
    private List<Integer> roomRefs = new ArrayList<>();

    /**
     * Ghi đè/thêm các slot khác null từ `incoming` vào this.
     * Scalar: OVERRIDE nếu incoming có giá trị. Utilities: UNION (MERGE).
     * §4.2 — dùng cho cả MERGE (refine) lẫn OVERRIDE (search).
     */
    public void mergeNonNull(Filters in) {
        if (in == null) return;
        if (in.priceMin != null) this.priceMin = in.priceMin;
        if (in.priceMax != null) this.priceMax = in.priceMax;
        if (in.location != null) this.location = in.location;
        if (in.poi != null)      this.poi = in.poi;
        if (in.radiusM != null)  this.radiusM = in.radiusM;
        if (in.areaMin != null)  this.areaMin = in.areaMin;
        if (in.roomType != null) this.roomType = in.roomType;
        if (in.datetime != null) this.datetime = in.datetime;
        if (in.utilities != null && !in.utilities.isEmpty()) {
            Set<String> union = new LinkedHashSet<>(this.utilities);
            union.addAll(in.utilities);
            this.utilities = new ArrayList<>(union);
        }
        if (in.roomRefs != null && !in.roomRefs.isEmpty()) {
            this.roomRefs = new ArrayList<>(in.roomRefs);
        }
    }

    /** Bản sao nông — dùng khi thử nới lỏng mà không đụng filter gốc (§5.3). */
    public Filters copy() {
        Filters c = new Filters();
        c.priceMin = priceMin; c.priceMax = priceMax; c.location = location;
        c.poi = poi; c.radiusM = radiusM; c.areaMin = areaMin;
        c.roomType = roomType; c.datetime = datetime;
        c.utilities = new ArrayList<>(utilities == null ? List.of() : utilities);
        c.roomRefs = new ArrayList<>(roomRefs == null ? List.of() : roomRefs);
        return c;
    }

    /** Xóa toàn bộ tiêu chí — dùng cho phép RESET (§4.2). */
    public void clear() {
        priceMin = null; priceMax = null; location = null; poi = null;
        radiusM = null; areaMin = null; roomType = null; datetime = null;
        utilities = new ArrayList<>();
        roomRefs = new ArrayList<>();
    }

    /** Có đủ ít nhất 1 slot định vị/giá để chạy search (§3.1, §4.3)? */
    public boolean hasAnyLocatorSlot() {
        return priceMax != null || priceMin != null || location != null || poi != null;
    }

    public boolean hasUtility(String key) {
        return utilities != null && utilities.contains(key);
    }

    /** Tóm tắt để hiển thị trong template fast-path (§6.3). */
    public String summary() {
        List<String> parts = new ArrayList<>();
        if (priceMax != null) parts.add("dưới " + formatVnd(priceMax));
        if (priceMin != null) parts.add("từ " + formatVnd(priceMin));
        if (location != null) parts.add("ở " + location);
        if (poi != null) parts.add("gần " + poi);
        if (areaMin != null) parts.add("trên " + areaMin + "m²");
        if (utilities != null && !utilities.isEmpty())
            parts.add("có " + String.join(", ", utilities.stream().map(Filters::utilityVi).toList()));
        return parts.isEmpty() ? "mọi tiêu chí" : String.join(", ", parts);
    }

    private static String formatVnd(long v) {
        if (v % 1_000_000 == 0) return (v / 1_000_000) + " triệu";
        return String.format("%,d", v).replace(',', '.') + "đ";
    }

    private static String utilityVi(String key) {
        return switch (key) {
            case "air_conditioner" -> "điều hòa";
            case "parking" -> "chỗ để xe";
            case "wifi" -> "wifi";
            case "washing_machine" -> "máy giặt";
            default -> key;
        };
    }
}
