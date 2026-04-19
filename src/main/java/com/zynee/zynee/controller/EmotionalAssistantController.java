package com.zynee.zynee.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zynee.zynee.service.EmotionalAssistantService;
import com.zynee.zynee.service.LocalAssistantProcessManager;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/emotional-assistant")
public class EmotionalAssistantController {

    private static final Logger log = LoggerFactory.getLogger(EmotionalAssistantController.class);
    private final EmotionalAssistantService emotionalAssistantService;
    private final LocalAssistantProcessManager localAssistantProcessManager;

    public EmotionalAssistantController(
            EmotionalAssistantService emotionalAssistantService,
            LocalAssistantProcessManager localAssistantProcessManager) {
        this.emotionalAssistantService = emotionalAssistantService;
        this.localAssistantProcessManager = localAssistantProcessManager;
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody Map<String, Object> payload, HttpSession session) {
        String email = (String) session.getAttribute("email");
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Please log in to use the assistant."));
        }

        String message = String.valueOf(payload.getOrDefault("message", "")).trim();
        if (message.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message cannot be empty."));
        }
        if (message.length() > 3000) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message is too long."));
        }

        String userName = (String) session.getAttribute("name");
        String countryCode = (String) session.getAttribute("countryCode");
        List<Map<String, String>> history = extractHistory(payload.get("history"));
        try {
            localAssistantProcessManager.ensureAssistantRunningOnDemand();
            String reply = emotionalAssistantService.getAssistantReply(message, userName, countryCode, history);
            return ResponseEntity.ok(Map.of("reply", reply));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error",
                            ex.getMessage() == null || ex.getMessage().isBlank()
                                    ? "Assistant service is not configured."
                                    : ex.getMessage()));
        } catch (IOException ex) {
            log.error("Failed to call local assistant service", ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", ex.getMessage() == null || ex.getMessage().isBlank()
                            ? "Assistant is temporarily unavailable. Please make sure local AI service is running."
                            : ex.getMessage()));
        }
    }

    private List<Map<String, String>> extractHistory(Object historyObj) {
        if (!(historyObj instanceof List<?> rawList)) {
            return List.of();
        }

        List<Map<String, String>> cleaned = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object roleObj = map.get("role");
            Object contentObj = map.get("content");
            if (!(roleObj instanceof String role) || !(contentObj instanceof String content)) {
                continue;
            }

            String normalizedRole = role.trim().toLowerCase();
            if (!normalizedRole.equals("user") && !normalizedRole.equals("assistant")) {
                continue;
            }

            String normalizedContent = content.trim();
            if (normalizedContent.isEmpty()) {
                continue;
            }
            if (normalizedContent.length() > 1000) {
                normalizedContent = normalizedContent.substring(0, 1000);
            }

            cleaned.add(Map.of("role", normalizedRole, "content", normalizedContent));
            if (cleaned.size() >= 12) {
                break;
            }
        }
        return cleaned;
    }
}
