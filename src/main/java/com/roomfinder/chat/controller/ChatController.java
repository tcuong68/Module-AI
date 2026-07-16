package com.roomfinder.chat.controller;

import com.roomfinder.chat.dto.ChatRequest;
import com.roomfinder.chat.dto.ChatResponse;
import com.roomfinder.chat.service.ChatOrchestrator;
import com.roomfinder.chat.service.ContextService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Hợp đồng API §7. */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatOrchestrator orchestrator;
    private final ContextService contextService;

    public ChatController(ChatOrchestrator orchestrator, ContextService contextService) {
        this.orchestrator = orchestrator;
        this.contextService = contextService;
    }

    /** §7.1 — POST /api/v1/chat */
    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return orchestrator.handle(request);
    }

    /** §7.2 — POST /api/v1/chat/reset : xóa chat:session:{id} khỏi Redis. */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset(@RequestBody Map<String, String> body) {
        String sessionId = body.get("session_id");
        if (sessionId == null) sessionId = body.get("sessionId");
        if (sessionId != null) contextService.delete(sessionId);
        return ResponseEntity.ok(Map.of("ok", true, "session_id", sessionId == null ? "" : sessionId));
    }

    /** Health check nhanh cho demo/CI. */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
