package com.roomfinder.chat.normalizer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chuẩn hóa span thời gian tiếng Việt → ISO-8601 (§3.3).
 * Xử lý các dạng phổ biến khi đặt lịch xem phòng: "chiều mai", "9h sáng thứ 3",
 * "3h chiều mai". Đây là bản heuristic cho MVP — đủ để book_appointment demo.
 */
public final class DateTimeNormalizer {

    private static final Pattern HOUR =
            Pattern.compile("(\\d{1,2})\\s*(?:h|giờ|:)\\s*(\\d{1,2})?");

    private DateTimeNormalizer() {}

    public static String normalize(String span) {
        if (span == null || span.isBlank()) return null;
        String s = span.toLowerCase().trim();

        LocalDate date = resolveDate(s);
        LocalTime time = resolveTime(s);
        if (time == null) return null;               // không có giờ → chưa đủ
        return LocalDateTime.of(date, time).toString();  // ISO-8601
    }

    private static LocalDate resolveDate(String s) {
        LocalDate today = LocalDate.now();
        if (s.contains("ngày kia")) return today.plusDays(2);
        if (s.contains("mai") || s.contains("ngày mai")) return today.plusDays(1);
        if (s.contains("hôm nay") || s.contains("nay")) return today;
        // "thứ 3".."thứ 7", "chủ nhật" → ngày tới gần nhất
        Matcher m = Pattern.compile("thứ\\s*([2-7])").matcher(s);
        if (m.find()) {
            int target = Integer.parseInt(m.group(1));       // thứ 2 = Monday(1)
            int isoTarget = target - 1;
            return nextWeekday(today, isoTarget);
        }
        if (s.contains("chủ nhật") || s.contains("cn")) return nextWeekday(today, 7);
        return today.plusDays(1);                     // mặc định: ngày mai
    }

    private static LocalTime resolveTime(String s) {
        int hour = -1, minute = 0;
        Matcher m = HOUR.matcher(s);
        if (m.find()) {
            hour = Integer.parseInt(m.group(1));
            if (m.group(2) != null && !m.group(2).isBlank())
                minute = Integer.parseInt(m.group(2));
        } else {
            if (s.contains("sáng")) hour = 9;
            else if (s.contains("trưa")) hour = 12;
            else if (s.contains("chiều")) hour = 15;
            else if (s.contains("tối")) hour = 19;
            else return null;
        }
        // quy đổi buổi cho giờ 1–11
        if (hour >= 1 && hour <= 11) {
            if (s.contains("chiều") || s.contains("tối")) hour += 12;
        }
        if (hour < 0 || hour > 23 || minute > 59) return null;
        return LocalTime.of(hour, minute);
    }

    private static LocalDate nextWeekday(LocalDate from, int isoDow) {
        int diff = (isoDow - from.getDayOfWeek().getValue() + 7) % 7;
        if (diff == 0) diff = 7;
        return from.plusDays(diff);
    }
}
