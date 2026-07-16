package com.roomfinder.chat.normalizer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chuẩn hóa span giá → số nguyên VND (§3.3).
 * "3tr5" → 3500000 | "3 củ" → 3000000 | "500k" → 500000 | "3000000" → 3000000
 *
 * Ở GĐ1 LLM thường đã trả số; normalizer này là mạng an toàn và là bước
 * BẮT BUỘC khi chuyển sang PhoBERT NER (GĐ2) vốn chỉ trả span văn bản.
 */
public final class PriceNormalizer {

    private static final Pattern MILLION =
            Pattern.compile("(\\d+)\\s*(triệu|tr|củ|chai)\\s*(\\d+)?");
    private static final Pattern THOUSAND =
            Pattern.compile("(\\d+)\\s*(nghìn|ngàn|k)");
    private static final Pattern RAW =
            Pattern.compile("(\\d{6,})");

    private PriceNormalizer() {}

    public static Long normalize(String span) {
        if (span == null) return null;
        String s = span.toLowerCase().replaceAll("[,.]", "");

        Matcher m = MILLION.matcher(s);
        if (m.find()) {
            long base = Long.parseLong(m.group(1)) * 1_000_000L;
            if (m.group(3) != null) {                // phần lẻ: "3tr5" = 3.5tr
                base += Long.parseLong(m.group(3)) * 100_000L;
            }
            return base;
        }
        m = THOUSAND.matcher(s);
        if (m.find()) return Long.parseLong(m.group(1)) * 1_000L;

        m = RAW.matcher(s);
        if (m.find()) return Long.parseLong(m.group(1));
        return null;
    }
}
