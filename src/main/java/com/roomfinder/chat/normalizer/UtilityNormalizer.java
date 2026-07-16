package com.roomfinder.chat.normalizer;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Chuẩn hóa tiện ích về enum key (§3.3): từ đồng nghĩa + bỏ dấu →
 * air_conditioner | parking | wifi | washing_machine.
 * Ví dụ: "điều hoà"/"điều hòa"/"máy lạnh" → air_conditioner.
 */
public final class UtilityNormalizer {

    /** key chuẩn → danh sách biến thể (đã bỏ dấu, lowercase). */
    private static final Map<String, List<String>> SYNONYMS = Map.of(
        "air_conditioner", List.of("dieu hoa", "dieu hoa nhiet do", "may lanh", "ac", "air conditioner"),
        "parking",         List.of("cho de xe", "de xe", "bai xe", "gui xe", "parking", "cho do xe", "do o to"),
        "wifi",            List.of("wifi", "mang", "internet"),
        "washing_machine", List.of("may giat", "giat")
    );

    private UtilityNormalizer() {}

    /** Chuẩn hóa 1 span → key hoặc null nếu không nhận diện được. */
    public static String normalizeOne(String span) {
        if (span == null) return null;
        String s = stripAccent(span.trim().toLowerCase());
        // đã là key chuẩn?
        if (SYNONYMS.containsKey(span.trim().toLowerCase())) return span.trim().toLowerCase();
        for (var e : SYNONYMS.entrySet()) {
            for (String variant : e.getValue()) {
                if (s.contains(variant)) return e.getKey();
            }
        }
        return null;
    }

    /** Quét cả câu, trả về mọi tiện ích xuất hiện (dùng cho NLU rule-based). */
    public static List<String> detectAll(String text) {
        if (text == null) return new ArrayList<>();
        String s = stripAccent(text.toLowerCase());
        Set<String> out = new LinkedHashSet<>();
        for (var e : SYNONYMS.entrySet()) {
            for (String variant : e.getValue()) {
                if (s.contains(variant)) { out.add(e.getKey()); break; }
            }
        }
        return new ArrayList<>(out);
    }

    /** Chuẩn hóa cả danh sách, loại trùng & phần tử không nhận diện được. */
    public static List<String> normalizeList(List<String> raw) {
        if (raw == null) return new ArrayList<>();
        Set<String> out = new LinkedHashSet<>();
        for (String r : raw) {
            String key = normalizeOne(r);
            if (key != null) out.add(key);
        }
        return new ArrayList<>(out);
    }

    private static String stripAccent(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "").replace("đ", "d");
    }
}
