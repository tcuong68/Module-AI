package com.roomfinder.chat.service;

import com.roomfinder.chat.config.NluProperties;
import com.roomfinder.chat.domain.Intent;
import com.roomfinder.chat.model.Filters;
import com.roomfinder.chat.model.NluResult;
import com.roomfinder.chat.normalizer.PriceNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NLU bằng PhoBERT qua nlu-service (FastAPI) — GĐ2, §11 bước 2.4.
 * @Primary thay cho LlmNluServiceImpl; chuỗi fallback 2 tầng:
 * PhoBERT chết/timeout(300ms) → LLM → rule-based. Hệ thống không bao giờ
 * sập vì NLU. Tắt bằng roomfinder.nlu.enabled=false (NLU_ENABLED).
 *
 * nlu-service trả entity là SPAN THÔ ({label,text,start,end,score}) — class này
 * quy span về slot của {@link Filters}: giá qua PriceNormalizer ngay tại đây
 * (cần Long để so sánh), các span chữ (location/datetime/utility) giữ nguyên
 * cho {@link com.roomfinder.chat.normalizer.EntityNormalizer} xử lý như mọi
 * NluService khác — không nhân đôi luật chuẩn hóa.
 */
@Service
@Primary
public class PhoBertNluServiceImpl implements NluService {

    private static final Logger log = LoggerFactory.getLogger(PhoBertNluServiceImpl.class);

    private final RestClient http;
    private final NluService fallback;
    private final NluProperties props;

    public PhoBertNluServiceImpl(NluProperties props,
                                 @Qualifier("llmNluServiceImpl") NluService fallback) {
        this.props = props;
        this.fallback = fallback;
        var f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(props.getTimeoutMs());
        f.setReadTimeout(props.getTimeoutMs());
        this.http = RestClient.builder().baseUrl(props.getUrl()).requestFactory(f).build();
    }

    @Override
    public NluResult parse(String message) {
        if (!props.isEnabled()) return fallback.parse(message);
        try {
            PhoBertResponse r = http.post()
                    .uri("/nlu")
                    .header("Content-Type", "application/json")
                    .body(Map.of("text", message == null ? "" : message))
                    .retrieve()
                    .body(PhoBertResponse.class);
            if (r == null || r.intent() == null) {
                throw new IllegalStateException("nlu-service trả response rỗng");
            }
            return toNluResult(r);
        } catch (Exception e) {
            log.warn("NLU PhoBERT lỗi ({}), fallback LLM", e.getMessage());
            return fallback.parse(message);
        }
    }

    // --- Hợp đồng JSON với nlu-service (xem nlu-service/README.md) --------

    record PhoBertResponse(String intent, double confidence, List<Span> entities) {}

    record Span(String label, String text, int start, int end, double score) {}

    // --- Span → Filters ---------------------------------------------------

    private NluResult toNluResult(PhoBertResponse r) {
        NluResult out = new NluResult();
        out.setIntent(Intent.fromCode(r.intent()));
        out.setConfidence(r.confidence());

        Filters f = new Filters();
        for (Span s : r.entities() == null ? List.<Span>of() : r.entities()) {
            String text = s.text();
            if (text == null || text.isBlank()) continue;
            switch (s.label()) {
                case "PRICE_MAX" -> f.setPriceMax(PriceNormalizer.normalize(text));
                case "PRICE_MIN" -> f.setPriceMin(PriceNormalizer.normalize(text));
                case "LOCATION"  -> f.setLocation(text);
                case "POI"       -> f.setPoi(text);
                case "RADIUS"    -> f.setRadiusM(parseRadiusM(text));
                case "AREA_MIN"  -> f.setAreaMin(parseFirstNumber(text));
                case "UTILITY"   -> f.getUtilities().add(text);
                case "ROOM_TYPE" -> f.setRoomType(mapRoomType(text));
                case "DATETIME"  -> f.setDatetime(text);
                case "ROOM_REF"  -> {
                    Integer ord = parseRoomRef(text);
                    if (ord != null && !f.getRoomRefs().contains(ord)) f.getRoomRefs().add(ord);
                }
                default -> log.debug("Bỏ qua entity label lạ: {}", s.label());
            }
        }
        out.setEntities(f);
        return out;
    }

    private static final Pattern RADIUS = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(km|m)?");

    private Integer parseRadiusM(String span) {
        Matcher m = RADIUS.matcher(span.toLowerCase());
        if (!m.find()) return null;
        double v = Double.parseDouble(m.group(1).replace(',', '.'));
        return (int) Math.round("km".equals(m.group(2)) ? v * 1000 : v);
    }

    private Double parseFirstNumber(String span) {
        Matcher m = Pattern.compile("(\\d+(?:[.,]\\d+)?)").matcher(span);
        return m.find() ? Double.parseDouble(m.group(1).replace(',', '.')) : null;
    }

    /**
     * "phòng số 2"/"phong thu hai"/"cái đầu tiên" → ordinal 1-based.
     * Chỉ cần 1..5 (top-K = 5); số bằng chữ vượt 5 coi như không phân giải được.
     */
    private Integer parseRoomRef(String span) {
        String s = strip(span);
        Matcher m = Pattern.compile("(\\d{1,2})").matcher(s);
        if (m.find()) return Integer.parseInt(m.group(1));
        if (s.contains("dau") || s.contains("nhat")) return 1;
        if (s.contains("hai")) return 2;
        if (s.contains("ba")) return 3;
        if (s.contains("bon") || s.contains("tu")) return 4;
        if (s.contains("nam")) return 5;
        return null;
    }

    /**
     * Span → mã room_type trong DB. Chỉ map khi người dùng nói rõ LOẠI HÌNH;
     * "khép kín" là thuộc tính (trọ lẫn CCMN đều có) — map thành PHONG_TRO sẽ
     * lọc oan chung cư mini, nên trả null (không filter).
     */
    private String mapRoomType(String span) {
        String s = strip(span);
        if (s.contains("nguyen can")) return "NHA_NGUYEN_CAN";
        if (s.contains("chung cu") || s.contains("ccmn")) return "CHUNG_CU_MINI";
        if (s.contains("tro")) return "PHONG_TRO";
        return null;
    }

    private String strip(String s) {
        String n = Normalizer.normalize(s.toLowerCase().trim(), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "").replace("đ", "d");
    }
}
