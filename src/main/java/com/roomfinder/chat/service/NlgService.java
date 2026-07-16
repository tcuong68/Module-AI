package com.roomfinder.chat.service;

import com.roomfinder.chat.config.LlmProperties;
import com.roomfinder.chat.domain.Room;
import com.roomfinder.chat.llm.LlmClient;
import com.roomfinder.chat.llm.LlmException;
import com.roomfinder.chat.model.Filters;
import com.roomfinder.chat.model.NlgOutcome;
import com.roomfinder.chat.model.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Tầng NLG (RAG) — §6. Serialize object phòng thành bảng gạch đầu dòng
 * (LLM đọc chính xác hơn JSON lồng nhau), gọi LLM, rồi chạy guardrail.
 * Khi guardrail fail: retry 1 lần với cảnh báo, vẫn fail → trả valid=false
 * để orchestrator rơi về fast-path template (an toàn tuyệt đối).
 */
@Service
public class NlgService {

    private static final Logger log = LoggerFactory.getLogger(NlgService.class);

    private static final String SYSTEM = """
        Bạn là trợ lý tư vấn phòng trọ của hệ thống RoomFinder.
        QUY TẮC TUYỆT ĐỐI:
        1. Chỉ được nói về các phòng có trong DANH SÁCH bên dưới.
        2. TUYỆT ĐỐI không bịa giá, diện tích, địa chỉ hay mã phòng.
        3. Nếu danh sách rỗng, hãy nói không tìm thấy và gợi ý nới điều kiện.
        4. Luôn nhắc mã phòng theo định dạng [#id] khi đề cập một phòng.
        5. Trả lời ngắn gọn, tối đa 4 câu, giọng thân thiện, xưng "mình".
        """;

    private static final String RETRY_WARNING = """

        CẢNH BÁO: Câu trả lời trước đã nhắc tới mã phòng hoặc con số KHÔNG có
        trong danh sách. Hãy viết lại, CHỈ dùng đúng mã [#id] và các con số
        xuất hiện trong DANH SÁCH PHÒNG. Không thêm bất kỳ số liệu nào khác.
        """;

    private final LlmClient llm;
    private final HallucinationValidator validator;
    private final double temperature;

    public NlgService(LlmClient llm, HallucinationValidator validator, LlmProperties props) {
        this.llm = llm;
        this.validator = validator;
        this.temperature = props.getGemini().getTemperatureNlg();
    }

    public NlgOutcome generate(String userMessage, List<Room> rooms, Filters filters, String relaxationNote) {
        String userContent = buildUserContent(userMessage, rooms, filters, relaxationNote);

        try {
            // Lượt 1
            String out = llm.complete(SYSTEM, userContent, temperature);
            ValidationResult v = validator.validate(out, rooms);
            if (v.ok()) return new NlgOutcome(out, true, false);

            log.warn("Guardrail bắt hallucination lượt 1: {}", v.reason());
            // Lượt 2 (retry có cảnh báo)
            String out2 = llm.complete(SYSTEM + RETRY_WARNING, userContent, temperature);
            ValidationResult v2 = validator.validate(out2, rooms);
            if (v2.ok()) return new NlgOutcome(out2, true, true);

            log.warn("Guardrail vẫn fail lượt 2: {} → fallback template", v2.reason());
            return new NlgOutcome(null, false, true);       // buộc fallback
        } catch (LlmException e) {
            log.warn("NLG LLM lỗi ({}) → fallback template", e.getMessage());
            return new NlgOutcome(null, false, false);
        }
    }

    /** Serialize từ object phòng — §6.1 (KHÔNG nhét raw JSON). */
    private String buildUserContent(String userMessage, List<Room> rooms, Filters filters, String relaxationNote) {
        StringBuilder sb = new StringBuilder();
        sb.append("[CONTEXT — DANH SÁCH PHÒNG]\n");
        if (rooms.isEmpty()) {
            sb.append("(trống)\n");
        } else {
            for (Room r : rooms) sb.append(serializeRoom(r)).append("\n");
        }
        sb.append("\n[BỘ LỌC ĐANG ÁP DỤNG]\n").append(filters.summary()).append("\n");
        if (relaxationNote != null) {
            sb.append("\n[LƯU Ý] Đã nới điều kiện: ").append(relaxationNote).append("\n");
        }
        sb.append("\n[USER]\n").append(userMessage);
        return sb.toString();
    }

    private String serializeRoom(Room r) {
        StringBuilder b = new StringBuilder();
        b.append("[#").append(r.getId()).append("] ")
         .append("Giá ").append(String.format("%,d", r.getPrice())).append("đ")
         .append(" | ").append(r.getArea()).append("m²")
         .append(" | ").append(r.getAddressText() == null ? r.getDistrict() : r.getAddressText())
         .append(" | Điều hòa: ").append(bool(r.getHasAirConditioner()))
         .append(" | Chỗ để xe: ").append(bool(r.getHasParking()))
         .append(" | Wifi: ").append(bool(r.getHasWifi()));
        if (r.getDistanceM() != null) {
            b.append(" | Cách điểm mốc ~").append(Math.round(r.getDistanceM())).append("m");
        }
        return b.toString();
    }

    private String bool(Boolean v) { return Boolean.TRUE.equals(v) ? "Có" : "Không"; }
}
