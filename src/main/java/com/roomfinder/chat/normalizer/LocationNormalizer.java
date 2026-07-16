package com.roomfinder.chat.normalizer;

import java.text.Normalizer;
import java.util.List;

/**
 * Chuẩn hóa tên khu vực → tên quận chuẩn (§3.3).
 * Fuzzy match không dấu với danh sách quận Hà Nội. Trong hệ thống thật,
 * danh sách này nên tra từ bảng `district`; MVP hardcode cho đơn giản.
 */
public final class LocationNormalizer {

    private static final List<String> DISTRICTS = List.of(
        "Thanh Xuân", "Cầu Giấy", "Hà Đông", "Đống Đa", "Hai Bà Trưng",
        "Ba Đình", "Hoàng Mai", "Nam Từ Liêm", "Bắc Từ Liêm", "Long Biên",
        "Tây Hồ", "Hoàn Kiếm"
    );

    private LocationNormalizer() {}

    /** Trả tên quận chuẩn nếu match, ngược lại trả nguyên (đã trim). */
    public static String normalize(String span) {
        if (span == null || span.isBlank()) return null;
        String needle = stripAccent(span.trim().toLowerCase());
        for (String d : DISTRICTS) {
            String hay = stripAccent(d.toLowerCase());
            if (hay.equals(needle) || hay.contains(needle) || needle.contains(hay)) {
                return d;
            }
        }
        return span.trim();
    }

    private static String stripAccent(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "").replace("đ", "d");
    }
}
