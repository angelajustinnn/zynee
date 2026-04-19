package com.zynee.zynee.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zynee.zynee.model.User;
import com.zynee.zynee.repository.UserRepository;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/data-analysis-consent")
public class DataAnalysisConsentController {

    private final UserRepository userRepository;

    public DataAnalysisConsentController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getConsentStatus(HttpSession session) {
        Optional<User> userOpt = getCurrentUser(session);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("authenticated", false, "message", "Login required."));
        }

        User user = userOpt.get();
        return ResponseEntity.ok(buildConsentResponse(user, null));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> updateConsent(
            @RequestBody Map<String, Object> payload,
            HttpSession session) {
        Optional<User> userOpt = getCurrentUser(session);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("authenticated", false, "message", "Login required."));
        }

        Boolean allow = parseAllowFlag(payload == null ? null : payload.get("allow"));
        if (allow == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "authenticated", true,
                            "updated", false,
                            "message", "Field 'allow' must be true or false."));
        }

        User user = userOpt.get();
        user.setDataAnalysisConsent(allow);
        user.setOnboardingTourCompleted(true);
        userRepository.save(user);

        if (session != null) {
            session.setAttribute("dataAnalysisConsent", allow);
            session.setAttribute("onboardingRequired", false);
            clearInsightsSummarySessionCache(session);
        }

        String message = allow
                ? "Data analysis permission is ON. Insights and reports are enabled."
                : "Data analysis permission is OFF. Logging features stay available, but analysis is disabled.";
        Map<String, Object> response = buildConsentResponse(user, message);
        response.put("updated", true);
        return ResponseEntity.ok(response);
    }

    private Optional<User> getCurrentUser(HttpSession session) {
        String email = session == null ? null : (String) session.getAttribute("email");
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmail(email);
    }

    private Map<String, Object> buildConsentResponse(User user, String messageOverride) {
        Boolean consent = user.getDataAnalysisConsent();
        boolean consentSet = consent != null;
        boolean analysisEnabled = Boolean.TRUE.equals(consent);

        String consentState = consentSet ? (analysisEnabled ? "allowed" : "declined") : "unset";
        String message;
        if (messageOverride != null && !messageOverride.isBlank()) {
            message = messageOverride;
        } else if (!consentSet) {
            message = "Please choose whether Zynee can analyze your mood, journal, and quick check-in data.";
        } else if (analysisEnabled) {
            message = "Analysis is enabled.";
        } else {
            message = "Analysis is disabled.";
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("authenticated", true);
        response.put("consentSet", consentSet);
        response.put("analysisEnabled", analysisEnabled);
        response.put("consentState", consentState);
        response.put("showPrompt", !consentSet);
        response.put("message", message);
        return response;
    }

    private Boolean parseAllowFlag(Object raw) {
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw instanceof String text) {
            String normalized = text.trim().toLowerCase();
            if ("true".equals(normalized) || "allow".equals(normalized) || "yes".equals(normalized)) {
                return true;
            }
            if ("false".equals(normalized) || "decline".equals(normalized) || "no".equals(normalized)) {
                return false;
            }
        }
        return null;
    }

    private void clearInsightsSummarySessionCache(HttpSession session) {
        session.removeAttribute("insightsSummaryDate");
        session.removeAttribute("insightsSummaryEmail");
        session.removeAttribute("insightsSummaryFingerprint");
        session.removeAttribute("insightsSummaryData");
    }
}
