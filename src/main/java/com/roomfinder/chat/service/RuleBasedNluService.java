package com.roomfinder.chat.service;

import com.roomfinder.chat.domain.Intent;
import com.roomfinder.chat.model.Filters;
import com.roomfinder.chat.model.NluResult;
import com.roomfinder.chat.normalizer.LocationNormalizer;
import com.roomfinder.chat.normalizer.PriceNormalizer;
import com.roomfinder.chat.normalizer.UtilityNormalizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    // Từ chỉ hướng phải nằm SÁT token giá (chỉ cách bởi khoảng trắng), không quét
    // cả câu: "Nam Từ Liêm" chứa "từ" và "trên 30m2" là diện tích — quét cả câu
    // sẽ gán nhầm price_min. group(1)=hướng trước, group(2)=giá, group(3)=hướng sau.
    private static final Pattern PRICE_TOKEN = Pattern.compile(
        "(dưới|trên|hơn|từ|tối thiểu|tối đa|không quá)?\\s*"
        + "(\\d+(?:[.,]\\d+)?\\s*(?:triệu|tr|củ|chai|nghìn|ngàn|k)\\d*|\\d{6,})"
        + "\\s*(trở lên|trở xuống)?");

    private static final List<String> PRICE_MIN_CUES = List.of("trên", "hơn", "từ", "tối thiểu");

    // Tham chiếu phòng theo thứ tự: "phòng 1", "phòng số 2", "so sánh phòng 1 và 2".
    // Lookahead loại số đi kèm đơn vị để "phòng 3 triệu"/"phòng 20m2" không thành ref.
    private static final String NOT_A_UNIT = "(?!\\s*(?:triệu|tr|củ|chai|nghìn|ngàn|k|m2|m²|\\d))";
    private static final Pattern ROOM_REF = Pattern.compile(
        "(?:phòng|căn|cái)\\s*(?:số|thứ)?\\s*(\\d{1,2})" + NOT_A_UNIT);
    private static final Pattern ROOM_REF_MORE = Pattern.compile(
        "\\s*(?:và|với|,)\\s*(\\d{1,2})" + NOT_A_UNIT);
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
            Long price = PriceNormalizer.normalize(pm.group(2));
            if (price != null) {
                String before = pm.group(1);
                boolean min = "trở lên".equals(pm.group(3))
                        || (before != null && PRICE_MIN_CUES.contains(before));
                if (min) f.setPriceMin(price);
                else     f.setPriceMax(price);   // mặc định: coi số là ngân sách tối đa
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

        f.setRoomRefs(detectRoomRefs(lower));

        r.setEntities(f);
        r.setIntent(detectIntent(lower, f));
        r.setConfidence(0.70);
        return r;
    }

    /**
     * "so sánh phòng 1 và 2" → [1, 2]. Chỉ nhận số nối tiếp NGAY SAU ref đầu
     * (qua "và"/"với"/","), tránh vơ số ở mệnh đề khác của câu.
     */
    private List<Integer> detectRoomRefs(String s) {
        Set<Integer> refs = new LinkedHashSet<>();
        Matcher m = ROOM_REF.matcher(s);
        if (m.find()) {
            refs.add(Integer.parseInt(m.group(1)));
            String tail = s.substring(m.end());
            Matcher more = ROOM_REF_MORE.matcher(tail);
            while (more.lookingAt()) {
                refs.add(Integer.parseInt(more.group(1)));
                tail = tail.substring(more.end());
                more = ROOM_REF_MORE.matcher(tail);
            }
        }
        return new ArrayList<>(refs);
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
