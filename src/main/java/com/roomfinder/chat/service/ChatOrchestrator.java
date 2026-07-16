package com.roomfinder.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomfinder.chat.config.ChatProperties;
import com.roomfinder.chat.domain.ChatLog;
import com.roomfinder.chat.domain.Intent;
import com.roomfinder.chat.domain.Room;
import com.roomfinder.chat.dto.ChatRequest;
import com.roomfinder.chat.dto.ChatResponse;
import com.roomfinder.chat.dto.MetaDto;
import com.roomfinder.chat.dto.RoomCardDto;
import com.roomfinder.chat.model.ChatContext;
import com.roomfinder.chat.model.Filters;
import com.roomfinder.chat.model.NlgOutcome;
import com.roomfinder.chat.model.NluResult;
import com.roomfinder.chat.model.RetrievalResult;
import com.roomfinder.chat.normalizer.EntityNormalizer;
import com.roomfinder.chat.repository.ChatLogRepository;
import com.roomfinder.chat.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bộ điều phối luồng chat — hiện thực sơ đồ §2.1:
 * NLU → Normalizer → Context (MERGE/OVERRIDE/RESET) → Slot Checker
 * → Retrieval → Fast-path / LLM-path + Guardrail → Response.
 */
@Service
public class ChatOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrator.class);

    private static final List<String> ADVISORY_CUES = List.of(
            "nên", "tư vấn", "so sánh", "vì sao", "tại sao", "cái nào tốt hơn", "tốt hơn", "gợi ý", "khuyên");
    private static final List<String> CHEAPER_CUES = List.of("rẻ hơn", "giảm giá", "hạ giá", "rẻ nữa");

    private final NluService nluService;
    private final EntityNormalizer normalizer;
    private final ContextService contextService;
    private final RetrievalService retrievalService;
    private final NlgService nlgService;
    private final RoomRepository roomRepo;
    private final ChatLogRepository chatLogRepo;
    private final ObjectMapper mapper;
    private final ChatProperties props;

    public ChatOrchestrator(NluService nluService, EntityNormalizer normalizer,
                            ContextService contextService, RetrievalService retrievalService,
                            NlgService nlgService, RoomRepository roomRepo,
                            ChatLogRepository chatLogRepo, ObjectMapper mapper, ChatProperties props) {
        this.nluService = nluService;
        this.normalizer = normalizer;
        this.contextService = contextService;
        this.retrievalService = retrievalService;
        this.nlgService = nlgService;
        this.roomRepo = roomRepo;
        this.chatLogRepo = chatLogRepo;
        this.mapper = mapper;
        this.props = props;
    }

    public ChatResponse handle(ChatRequest req) {
        long t0 = System.currentTimeMillis();
        String sessionId = (req.getSessionId() == null || req.getSessionId().isBlank())
                ? "s-" + UUID.randomUUID() : req.getSessionId();
        String message = req.getMessage();

        ChatContext ctx = contextService.load(sessionId);
        if (req.getUserId() != null) ctx.setUserId(req.getUserId());

        // 1) NLU + 2) Normalizer
        NluResult nlu = nluService.parse(message);
        if (nlu.getEntities() == null) nlu.setEntities(new Filters());
        normalizer.normalize(nlu.getEntities());

        // Slot đang chờ (§4.3): coi câu trả lời là tiếp nối tìm kiếm
        if (ctx.getPendingSlot() != null && nlu.getEntities().hasAnyLocatorSlot()) {
            nlu.setIntent(Intent.SEARCH_ROOM);
        }
        ctx.setPendingSlot(null);

        // 3) Context: MERGE / OVERRIDE / RESET
        contextService.apply(ctx, nlu, message);
        Filters af = ctx.getActiveFilters();

        // Rule "rẻ hơn nữa đi" (§4.2): refine không kèm giá → giảm % giá hiện tại
        if (nlu.getIntent() == Intent.REFINE_SEARCH
                && nlu.getEntities().getPriceMax() == null
                && af.getPriceMax() != null && containsAny(message, CHEAPER_CUES)) {
            long reduced = Math.round(af.getPriceMax() * (100.0 - props.getCheaperStepPercent()) / 100.0);
            af.setPriceMax(reduced);
            log.debug("Rule rẻ hơn: price_max → {}", reduced);
        }

        ChatResponse resp = switch (nlu.getIntent()) {
            case SEARCH_ROOM, REFINE_SEARCH -> handleSearch(ctx, nlu, message, sessionId, t0);
            case ROOM_DETAIL   -> handleRoomDetail(ctx, nlu, sessionId, t0);
            case COMPARE_ROOMS -> handleCompare(ctx, nlu, sessionId, t0);
            case CALCULATE_COST -> handleCalculateCost(ctx, nlu, sessionId, t0);
            case BOOK_APPOINTMENT -> handleBooking(ctx, nlu, sessionId, t0);
            case POLICY_INQUIRY -> simpleReply(ctx, nlu, sessionId, t0,
                    "Về hợp đồng & đặt cọc: thường cọc 1 tháng, hợp đồng tối thiểu 6 tháng, "
                            + "báo trước 30 ngày khi trả phòng. Bạn muốn hỏi cụ thể phòng nào để mình xem điều khoản nhé?");
            case OUT_OF_SCOPE -> simpleReply(ctx, nlu, sessionId, t0,
                    "Mình là trợ lý tìm phòng trọ. Bạn cho mình biết khu vực và mức giá mong muốn, "
                            + "ví dụ: \"Tìm phòng dưới 3 triệu ở Thanh Xuân\" nhé!");
        };
        return resp;
    }

    // --- SEARCH / REFINE -------------------------------------------------

    private ChatResponse handleSearch(ChatContext ctx, NluResult nlu, String message,
                                      String sessionId, long t0) {
        Filters af = ctx.getActiveFilters();

        // Slot Checker (§4.3): thiếu cả giá lẫn khu vực → hỏi lại, KHÔNG gọi LLM
        if (!af.hasAnyLocatorSlot()) {
            ctx.setPendingSlot("location");
            contextService.save(ctx);
            String q = "Bạn muốn tìm phòng ở khu vực nào ạ? Và tài chính tối đa khoảng bao nhiêu?";
            logTurn(sessionId, message, nlu, List.of(), "CLARIFY", false, t0);
            return build(sessionId, q, nlu.getIntent(), List.of(), af,
                    "CLARIFY", false, nlu.getConfidence(), false, t0);
        }

        RetrievalResult rr = retrievalService.search(af);
        List<Room> rooms = rr.getRooms();
        ctx.setLastResultIds(rooms.stream().map(Room::getId).toList());

        boolean advisory = containsAny(message, ADVISORY_CUES);
        boolean fastPath = canFastPath(nlu, rooms, rr.isRelaxed(), advisory);

        String reply;
        String path;
        boolean hallucination = false;

        if (rooms.isEmpty()) {
            reply = "Hiện chưa có phòng nào phù hợp với yêu cầu của bạn. "
                    + "Bạn thử nới mức giá hoặc mở rộng khu vực nhé.";
            path = "FAST";
        } else if (fastPath) {
            reply = listReply(rooms, af, rr.getRelaxationNote());
            path = "FAST";
        } else {
            NlgOutcome out = nlgService.generate(message, rooms, af, rr.getRelaxationNote());
            hallucination = out.hallucinationDetected();
            if (out.valid()) {
                reply = out.reply();
                path = "LLM";
            } else {
                // LLM lỗi hoặc guardrail chặn → câu trả lời thực tế là template.
                // Ghi TEMPLATE để metric không báo "LLM" khi LLM không hề chạy;
                // hallucination_flag phân biệt guardrail chặn (true) vs LLM lỗi (false).
                reply = listReply(rooms, af, rr.getRelaxationNote());   // fallback an toàn
                path = "TEMPLATE";
            }
        }

        contextService.save(ctx);
        logTurn(sessionId, message, nlu, rooms, path, hallucination, t0);
        return build(sessionId, reply, nlu.getIntent(), cards(rooms), af,
                path, rr.isRelaxed(), nlu.getConfidence(), hallucination, t0);
    }

    // --- ROOM_DETAIL -----------------------------------------------------

    private ChatResponse handleRoomDetail(ChatContext ctx, NluResult nlu, String sessionId, long t0) {
        List<Room> refs = resolveRefs(ctx, nlu);
        if (refs.isEmpty() && namedARoom(nlu)) {
            return simpleReply(ctx, nlu, sessionId, t0, unresolvedRefReply(ctx));
        }
        if (refs.isEmpty()) {
            return simpleReply(ctx, nlu, sessionId, t0,
                    "Bạn muốn xem chi tiết phòng nào? Hãy nói \"phòng số 1\", \"phòng số 2\"... nhé.");
        }
        Room r = refs.get(0);
        String reply = String.format(
                "Phòng [#%d] — %s. Giá %,dđ/tháng, diện tích %sm², tại %s. Điều hòa: %s, chỗ để xe: %s.",
                r.getId(), r.getTitle(), r.getPrice(), r.getArea(),
                r.getAddressText(), yn(r.getHasAirConditioner()), yn(r.getHasParking()));
        contextService.save(ctx);
        logTurn(sessionId, nlu != null ? nlu.getIntent().getCode() : "", nlu, refs, "FAST", false, t0);
        return build(sessionId, reply, nlu.getIntent(), cards(refs), ctx.getActiveFilters(),
                "FAST", false, nlu.getConfidence(), false, t0);
    }

    // --- COMPARE_ROOMS ---------------------------------------------------

    private ChatResponse handleCompare(ChatContext ctx, NluResult nlu, String sessionId, long t0) {
        List<Room> refs = resolveRefs(ctx, nlu);
        if (refs.size() < 2 && namedARoom(nlu)) {
            return simpleReply(ctx, nlu, sessionId, t0, unresolvedRefReply(ctx));
        }
        if (refs.size() < 2) {
            // Người dùng không chỉ định phòng nào → mặc định 2 phòng đầu kết quả trước.
            refs = topN(ctx, 2);
        }
        if (refs.size() < 2) {
            return simpleReply(ctx, nlu, sessionId, t0,
                    "Mình cần ít nhất 2 phòng để so sánh. Bạn tìm phòng trước rồi nói \"so sánh phòng 1 và 2\" nhé.");
        }
        StringBuilder sb = new StringBuilder("So sánh nhanh:\n");
        for (Room r : refs) {
            sb.append(String.format("• [#%d] %,dđ | %sm² | %s | ĐH:%s, xe:%s%n",
                    r.getId(), r.getPrice(), r.getArea(), r.getDistrict(),
                    yn(r.getHasAirConditioner()), yn(r.getHasParking())));
        }
        contextService.save(ctx);
        logTurn(sessionId, "compare", nlu, refs, "FAST", false, t0);
        return build(sessionId, sb.toString().trim(), nlu.getIntent(), cards(refs),
                ctx.getActiveFilters(), "FAST", false, nlu.getConfidence(), false, t0);
    }

    // --- CALCULATE_COST --------------------------------------------------

    private ChatResponse handleCalculateCost(ChatContext ctx, NluResult nlu, String sessionId, long t0) {
        List<Room> refs = resolveRefs(ctx, nlu);
        // Người dùng ĐÃ nói rõ phòng nào mà không phân giải được → hỏi lại.
        // Rơi về topN ở đây sẽ trả lời tự tin về SAI phòng (xem unresolvedRefReply).
        if (refs.isEmpty() && namedARoom(nlu)) {
            return simpleReply(ctx, nlu, sessionId, t0, unresolvedRefReply(ctx));
        }
        if (refs.isEmpty()) refs = topN(ctx, 1);
        if (refs.isEmpty()) {
            return simpleReply(ctx, nlu, sessionId, t0,
                    "Bạn muốn tính chi phí cho phòng nào? Hãy tìm phòng rồi nói \"tính chi phí phòng 1\" nhé.");
        }
        Room r = refs.get(0);
        long dien = 100_000, nuoc = 100_000, dichVu = 150_000;   // ước tính mặc định
        long tong = r.getPrice() + dien + nuoc + dichVu;
        String reply = String.format(
                "Ước tính chi phí/tháng cho phòng [#%d]: tiền phòng %,dđ + điện ~%,dđ + nước ~%,dđ + dịch vụ ~%,dđ ≈ %,dđ.",
                r.getId(), r.getPrice(), dien, nuoc, dichVu, tong);
        contextService.save(ctx);
        logTurn(sessionId, "calculate_cost", nlu, refs, "FAST", false, t0);
        return build(sessionId, reply, nlu.getIntent(), cards(refs), ctx.getActiveFilters(),
                "FAST", false, nlu.getConfidence(), false, t0);
    }

    // --- BOOK_APPOINTMENT (chỉ thu thập tham số — §1.2) ------------------

    private ChatResponse handleBooking(ChatContext ctx, NluResult nlu, String sessionId, long t0) {
        List<Room> refs = resolveRefs(ctx, nlu);
        String datetime = nlu.getEntities().getDatetime();
        if (refs.isEmpty()) {
            return simpleReply(ctx, nlu, sessionId, t0,
                    "Bạn muốn đặt lịch xem phòng nào và vào lúc nào ạ? Ví dụ: \"đặt lịch xem phòng 1 chiều mai lúc 3h\".");
        }
        if (datetime == null) {
            return simpleReply(ctx, nlu, sessionId, t0,
                    "Bạn muốn xem phòng [#" + refs.get(0).getId() + "] vào thời gian nào ạ?");
        }
        // Không ghi DB — chỉ gọi API nghiệp vụ (đây là chỗ tích hợp). MVP: xác nhận.
        String reply = String.format(
                "Mình đã ghi nhận yêu cầu đặt lịch xem phòng [#%d] vào %s và chuyển tới hệ thống đặt lịch. "
                        + "Bạn sẽ nhận xác nhận sớm nhé!", refs.get(0).getId(), datetime);
        contextService.save(ctx);
        logTurn(sessionId, "book_appointment", nlu, refs, "FAST", false, t0);
        return build(sessionId, reply, nlu.getIntent(), cards(refs), ctx.getActiveFilters(),
                "FAST", false, nlu.getConfidence(), false, t0);
    }

    // --- Helpers ---------------------------------------------------------

    private ChatResponse simpleReply(ChatContext ctx, NluResult nlu, String sessionId, long t0, String text) {
        contextService.save(ctx);
        logTurn(sessionId, text, nlu, List.of(), "FAST", false, t0);
        return build(sessionId, text, nlu.getIntent(), List.of(), ctx.getActiveFilters(),
                "FAST", false, nlu.getConfidence(), false, t0);
    }

    /** Điều kiện fast-path — §6.3. */
    private boolean canFastPath(NluResult nlu, List<Room> rooms, boolean relaxed, boolean advisory) {
        return (nlu.getIntent() == Intent.SEARCH_ROOM || nlu.getIntent() == Intent.REFINE_SEARCH)
                && nlu.getConfidence() >= props.getFastPath().getConfidenceThreshold()
                && !rooms.isEmpty()
                && !relaxed
                && !advisory;
    }

    private String listReply(List<Room> rooms, Filters af, String relaxationNote) {
        if (relaxationNote != null) {
            return String.format(
                    "Mình không thấy phòng khớp đúng yêu cầu nên đã %s, tìm được %d phòng. Bạn xem thử nhé:",
                    relaxationNote, rooms.size());
        }
        return String.format("Mình tìm được %d phòng phù hợp với yêu cầu của bạn (%s). Bạn xem thử nhé:",
                rooms.size(), af.summary());
    }

    /** Người dùng có chỉ đích danh phòng nào không (dù có phân giải được hay không)? */
    private boolean namedARoom(NluResult nlu) {
        List<Integer> refs = nlu.getEntities().getRoomRefs();
        return refs != null && !refs.isEmpty();
    }

    /**
     * Khi người dùng nhắc một phòng mà không phân giải được: hỏi lại thay vì
     * đoán. Đoán ở đây tạo ra câu trả lời SAI mà nghe vẫn tự tin — người dùng
     * không có cách nào biết bot vừa đổi sang phòng khác.
     */
    private String unresolvedRefReply(ChatContext ctx) {
        List<Long> last = ctx.getLastResultIds();
        if (last == null || last.isEmpty()) {
            return "Bạn cho mình biết phòng nào nhé — hãy tìm phòng trước rồi nói \"phòng 1\", \"phòng 2\"...";
        }
        return String.format(
                "Mình chưa rõ bạn muốn nói phòng nào. Danh sách vừa rồi có %d phòng, "
                        + "bạn nói giúp mình \"phòng 1\"… \"phòng %d\" nhé.", last.size(), last.size());
    }

    /** Giải nghĩa room_refs (ordinal 1-based) theo last_result_ids trong context. */
    private List<Room> resolveRefs(ChatContext ctx, NluResult nlu) {
        List<Integer> refs = nlu.getEntities().getRoomRefs();
        List<Long> last = ctx.getLastResultIds();
        if (refs == null || refs.isEmpty() || last == null || last.isEmpty()) return List.of();
        List<Long> ids = new ArrayList<>();
        for (int ord : refs) {
            if (ord >= 1 && ord <= last.size()) ids.add(last.get(ord - 1));
            else if (last.contains((long) ord)) ids.add((long) ord);   // ref là id thật
        }
        return orderByIds(roomRepo.findAllById(ids), ids);
    }

    private List<Room> topN(ChatContext ctx, int n) {
        List<Long> last = ctx.getLastResultIds();
        if (last == null || last.isEmpty()) return List.of();
        List<Long> ids = last.stream().limit(n).toList();
        return orderByIds(roomRepo.findAllById(ids), ids);
    }

    private List<Room> orderByIds(List<Room> rooms, List<Long> ids) {
        List<Room> out = new ArrayList<>();
        for (Long id : ids) rooms.stream().filter(r -> r.getId().equals(id)).findFirst().ifPresent(out::add);
        return out;
    }

    private List<RoomCardDto> cards(List<Room> rooms) {
        return rooms.stream().map(RoomCardDto::from).toList();
    }

    private boolean containsAny(String message, List<String> cues) {
        String s = message == null ? "" : message.toLowerCase();
        return cues.stream().anyMatch(s::contains);
    }

    private String yn(Boolean v) { return Boolean.TRUE.equals(v) ? "có" : "không"; }

    private ChatResponse build(String sessionId, String reply, Intent intent, List<RoomCardDto> rooms,
                               Filters af, String path, boolean relaxed, double conf,
                               boolean hallucination, long t0) {
        return ChatResponse.builder()
                .sessionId(sessionId)
                .reply(reply)
                .intent(intent.getCode())
                .rooms(rooms)
                .activeFilters(af)
                .meta(MetaDto.builder()
                        .path(path)
                        .relaxed(relaxed)
                        .latencyMs(System.currentTimeMillis() - t0)
                        .nluConfidence(conf)
                        .hallucinationDetected(hallucination)
                        .build())
                .build();
    }

    private void logTurn(String sessionId, String message, NluResult nlu, List<Room> rooms,
                         String path, boolean hallucination, long t0) {
        try {
            ChatLog cl = new ChatLog();
            cl.setSessionId(sessionId);
            cl.setUserMessage(message);
            cl.setPredictedIntent(nlu.getIntent().getCode());
            cl.setNluConfidence((float) nlu.getConfidence());
            cl.setExtractedEntities(mapper.writeValueAsString(nlu.getEntities()));
            cl.setResultRoomIds(mapper.writeValueAsString(rooms.stream().map(Room::getId).toList()));
            cl.setPath(path);
            cl.setHallucinationFlag(hallucination);
            cl.setLatencyMs((int) (System.currentTimeMillis() - t0));
            chatLogRepo.save(cl);
        } catch (Exception e) {
            log.warn("Ghi chat_log lỗi: {}", e.getMessage());
        }
    }
}
