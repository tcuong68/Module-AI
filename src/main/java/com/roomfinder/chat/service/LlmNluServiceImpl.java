package com.roomfinder.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomfinder.chat.llm.LlmClient;
import com.roomfinder.chat.llm.LlmException;
import com.roomfinder.chat.model.NluResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Cài đặt NLU bằng LLM trả JSON — GĐ1 (§10 bước 1.1).
 * Từ GĐ2, bean NLU mặc định là PhoBertNluServiceImpl (@Primary); class này
 * thành tầng fallback thứ nhất khi nlu-service chết/timeout.
 *
 * An toàn: nếu LLM lỗi/không có key → fallback sang NLU rule-based để
 * hệ thống không sập vì NLU (§11 bước 2.3).
 */
@Service
public class LlmNluServiceImpl implements NluService {

    private static final Logger log = LoggerFactory.getLogger(LlmNluServiceImpl.class);

    private static final String SYSTEM = """
        Bạn là bộ phân tích ngôn ngữ cho hệ thống tìm phòng trọ.
        Với câu tiếng Việt của người dùng, hãy trả về DUY NHẤT một object JSON,
        KHÔNG markdown, KHÔNG giải thích, KHÔNG code fence.
        Schema:
        {"intent": <một trong: search_room|refine_search|room_detail|
                    compare_rooms|book_appointment|calculate_cost|
                    policy_inquiry|out_of_scope>,
         "confidence": <số 0..1>,
         "entities": {"price_min":null,"price_max":null,"location":null,
                      "poi":null,"radius_m":null,"area_min":null,
                      "utilities":[],"room_type":null,"datetime":null,
                      "room_refs":[]}}
        Quy ước:
        - null nghĩa là người dùng KHÔNG nhắc đến (khác với false = "không cần").
        - Giá quy đổi ra số nguyên VND: "3 triệu"->3000000, "3tr5"->3500000, "500k"->500000.
        - utilities dùng key chuẩn: air_conditioner, parking, wifi, washing_machine.
        - "rẻ hơn nữa", "gần hơn", "có ... nữa" là refine_search (không phải search_room mới).
        - location là tên quận; poi là địa điểm mốc như "PTIT","Bách Khoa".

        Ví dụ:
        User: "Tìm phòng dưới 3 triệu ở Thanh Xuân có điều hòa"
        {"intent":"search_room","confidence":0.96,"entities":{"price_min":null,
        "price_max":3000000,"location":"Thanh Xuân","poi":null,"radius_m":null,
        "area_min":null,"utilities":["air_conditioner"],"room_type":null,
        "datetime":null,"room_refs":[]}}
        User: "Có phòng nào gần PTIT không"
        {"intent":"search_room","confidence":0.93,"entities":{"price_min":null,
        "price_max":null,"location":null,"poi":"PTIT","radius_m":null,
        "area_min":null,"utilities":[],"room_type":null,"datetime":null,"room_refs":[]}}
        User: "Rẻ hơn nữa đi"
        {"intent":"refine_search","confidence":0.9,"entities":{"price_min":null,
        "price_max":null,"location":null,"poi":null,"radius_m":null,"area_min":null,
        "utilities":[],"room_type":null,"datetime":null,"room_refs":[]}}
        """;

    private final LlmClient llm;
    private final NluService fallback;
    private final ObjectMapper mapper;

    public LlmNluServiceImpl(LlmClient llm,
                             @Qualifier("ruleBasedNluService") NluService fallback,
                             ObjectMapper mapper) {
        this.llm = llm;
        this.fallback = fallback;
        this.mapper = mapper;
    }

    @Override
    public NluResult parse(String message) {
        try {
            String raw = llm.complete(SYSTEM, message, 0.0);
            String json = stripFence(raw);
            return mapper.readValue(json, NluResult.class);
        } catch (LlmException e) {
            log.warn("NLU LLM lỗi ({}), fallback rule-based", e.getMessage());
            return fallback.parse(message);
        } catch (Exception e) {
            log.warn("NLU parse JSON lỗi ({}), fallback rule-based", e.getMessage());
            return fallback.parse(message);
        }
    }

    /** Bỏ ```json ... ``` nếu LLM lỡ bọc code fence. */
    private String stripFence(String raw) {
        String s = raw.replaceAll("```json", "").replaceAll("```", "").trim();
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        return (start >= 0 && end > start) ? s.substring(start, end + 1) : s;
    }
}
