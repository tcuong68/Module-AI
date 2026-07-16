package com.roomfinder.chat.service;

import com.roomfinder.chat.domain.Intent;
import com.roomfinder.chat.model.Filters;
import com.roomfinder.chat.model.NluResult;
import com.roomfinder.chat.normalizer.LocationNormalizer;
import com.roomfinder.chat.normalizer.PriceNormalizer;
import com.roomfinder.chat.normalizer.UtilityNormalizer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NLU dự phòng bằng luật (regex + từ khóa). KHÔNG phải phương án chính,
 * chỉ dùng khi LLM lỗi/không có API key để "hệ thống không bao giờ sập vì NLU"
 * (§11 bước 2.3). Độ chính xác thấp hơn LLM nên confidence để 0.7.
 */
@Component("ruleBasedNluService")
public class RuleBasedNluService implements NluService {

    private static final List<String> DISTRICTS = List.of(
        "Thanh Xuân", "Cầu Giấy", "Hà Đông", "Đống Đa", "Hai Bà Trưng",
        "Ba Đình", "Hoàng Mai", "Nam Từ Liêm", "Bắc Từ Liêm", "Long Biên", "Tây Hồ");

    // Từ khóa POI → chuỗi để RetrievalService khớp alias trong bảng poi
    private static final List<String> POI_KEYS = List.of("PTIT", "Bách Khoa", "Mỹ Đình");

    private static final Pattern PRICE_TOKEN = Pattern.compile(
        "(\\d+(?:[.,]\\d+)?\\s*(?:triệu|tr|củ|chai|nghìn|ngàn|k)\\d*|\\d{6,})");
    private static final Pattern AREA = Pattern.compile("(?:trên|từ)?\\s*(\\d{1,3})\\s*m2|(\\d{1,3})\\s*m²");

    @Override
    public NluResult parse(String message) {
        String m = message == null ? "" : message.trim();
        String lower = m.toLowerCase();

        NluResult r = new NluResult();
        Filters f = new Filters();

        // --- Entities ---
        Matcher pm = PRICE_TOKEN.matcher(lower);
        if (pm.find()) {
            Long price = PriceNormalizer.normalize(pm.group(1));
            if (price != null) {
                if (lower.contains("trở lên") || lower.contains("tối thiểu")
                        || lower.contains("từ") && lower.contains("trở"))
                    f.setPriceMin(price);
                else
                    f.setPriceMax(price);
            }
        }
        for (String d : DISTRICTS) {
            if (stripAccentContains(lower, d)) { f.setLocation(LocationNormalizer.normalize(d)); break; }
        }
        for (String p : POI_KEYS) {
            if (stripAccentContains(lower, p)) { f.setPoi(p); break; }
        }
        f.setUtilities(UtilityNormalizer.detectAll(lower));
        Matcher am = AREA.matcher(lower);
        if (am.find()) {
            String g = am.group(1) != null ? am.group(1) : am.group(2);
            if (g != null) f.setAreaMin(Double.parseDouble(g));
        }

        r.setEntities(f);
        r.setIntent(detectIntent(lower, f));
        r.setConfidence(0.70);
        return r;
    }

    private Intent detectIntent(String s, Filters f) {
        if (s.matches(".*\\b(chào|hi|hello|xin chào|giúp gì|bạn là ai).*")) return Intent.OUT_OF_SCOPE;
        if (s.contains("so sánh")) return Intent.COMPARE_ROOMS;
        if (s.contains("đặt lịch") || s.contains("hẹn") || s.contains("đi xem")) return Intent.BOOK_APPOINTMENT;
        if (s.contains("chi phí") || s.contains("tính tiền") || s.contains("tổng cộng")) return Intent.CALCULATE_COST;
        if (s.contains("hợp đồng") || s.contains("quy định") || s.contains("chính sách")
                || s.contains("đặt cọc") || s.contains("cọc")) return Intent.POLICY_INQUIRY;
        if (s.contains("chi tiết") || s.matches(".*phòng (số|thứ) .*")) return Intent.ROOM_DETAIL;
        // Refinement: câu ngắn, có từ so sánh/thêm nhưng ít tiêu chí mới
        boolean refineCue = s.contains("rẻ hơn") || s.contains("gần hơn")
                || s.contains("nữa") || s.contains("thêm") || s.contains("còn");
        boolean searchCue = s.contains("tìm") || s.contains("kiếm") || s.contains("thuê") || s.contains("phòng");
        if (refineCue && !searchCue) return Intent.REFINE_SEARCH;
        if (searchCue || f.hasAnyLocatorSlot()) return Intent.SEARCH_ROOM;
        return Intent.OUT_OF_SCOPE;
    }

    private boolean stripAccentContains(String haystackLower, String needle) {
        String h = strip(haystackLower);
        String n = strip(needle.toLowerCase());
        return h.contains(n);
    }

    private String strip(String s) {
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "").replace("đ", "d");
    }
}
