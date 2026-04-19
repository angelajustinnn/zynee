package com.zynee.zynee.controller;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zynee.zynee.model.QuickCheckinEntry;
import com.zynee.zynee.model.User;
import com.zynee.zynee.repository.QuickCheckinEntryRepository;
import com.zynee.zynee.repository.UserRepository;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/quick-checkins")
public class QuickCheckinApiController {

    private static final String DEFAULT_VERSION = "v2.0-ml-ready";
    private static final int DEFAULT_QUESTION_COUNT = 14;
    private static final Logger log = LoggerFactory.getLogger(QuickCheckinApiController.class);

    private final UserRepository userRepository;
    private final QuickCheckinEntryRepository quickCheckinEntryRepository;
    private final ObjectMapper objectMapper;

    public QuickCheckinApiController(
            UserRepository userRepository,
            QuickCheckinEntryRepository quickCheckinEntryRepository,
            ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.quickCheckinEntryRepository = quickCheckinEntryRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> saveQuickCheckin(
            @RequestBody Map<String, Object> payload,
            HttpSession session) {
        try {
            Optional<User> userOpt = getCurrentUser(session);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "Login required"));
            }

            Map<String, Object> answers = normalizeAnswerMap(payload.get("answers"));
            if (answers.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "No answers were provided."));
            }

            int questionCount = parseQuestionCount(payload.get("questionCount"));
            int answeredCount = countAnswered(answers);
            if (answeredCount == 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "At least one answered question is required."));
            }

            Integer overallMoodScore = extractScore(answers, "overall_mood");
            Integer stressScore = extractScore(answers, "stress_load");
            Integer anxietyScore = extractScore(answers, "anxiety_load");
            Integer energyScore = extractScore(answers, "energy");
            Integer sleepScore = extractScore(answers, "sleep_quality");
            Integer focusScore = firstPresentScore(
                    extractScore(answers, "focus"),
                    extractScore(answers, "support_need"),
                    extractScore(answers, "hope_level"));
            Integer motivationScore = extractScore(answers, "motivation");
            Integer socialScore = extractScore(answers, "social_connection");
            Integer physicalScore = firstPresentScore(
                    extractScore(answers, "body_signal"),
                    extractScore(answers, "energy"),
                    extractScore(answers, "sleep_quality"));
            Integer regulationScore = firstPresentScore(
                    extractScore(answers, "emotional_regulation"),
                    extractScore(answers, "stress_load"),
                    extractScore(answers, "anxiety_load"));
            Integer hopeScore = extractScore(answers, "hope_level");
            Integer supportScore = extractScore(answers, "support_need");

            List<Integer> numericScores = collectNonNullScores(
                    overallMoodScore,
                    stressScore,
                    anxietyScore,
                    energyScore,
                    sleepScore,
                    focusScore,
                    motivationScore,
                    socialScore,
                    physicalScore,
                    regulationScore,
                    hopeScore,
                    supportScore);

            if (numericScores.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Numeric wellness signals are missing."));
            }

            double averageScore = numericScores.stream().mapToInt(Integer::intValue).average().orElse(0.0);
            int lowSignalCount = (int) numericScores.stream().filter(score -> score <= 2).count();
            int responseConfidence = clamp((int) Math.round((answeredCount * 100.0) / Math.max(1, questionCount)), 0, 100);
            String moodLabel = deriveMoodLabel(averageScore, lowSignalCount);

            QuickCheckinEntry entry = new QuickCheckinEntry();
            entry.setUser(userOpt.get());
            entry.setCreatedAt(LocalDateTime.now());
            entry.setQuestionnaireVersion(sanitize(
                    payload.get("version"),
                    DEFAULT_VERSION,
                    40));
            entry.setAnsweredCount(answeredCount);
            entry.setResponseConfidence(responseConfidence);
            entry.setAverageScore(round2(averageScore));
            entry.setMoodLabel(moodLabel);
            entry.setLowSignalCount(lowSignalCount);
            entry.setOverallMoodScore(overallMoodScore);
            entry.setStressScore(stressScore);
            entry.setAnxietyScore(anxietyScore);
            entry.setEnergyScore(energyScore);
            entry.setSleepScore(sleepScore);
            entry.setFocusScore(focusScore);
            entry.setMotivationScore(motivationScore);
            entry.setSocialScore(socialScore);
            entry.setPhysicalScore(physicalScore);
            entry.setRegulationScore(regulationScore);
            entry.setHopeScore(hopeScore);
            entry.setSupportScore(supportScore);
            entry.setPrimaryTrigger(sanitize(extractText(answers, "primary_trigger"), "", 120));
            entry.setQuickNote(sanitize(extractText(answers, "one_small_need"), "", 600));
            entry.setMoodTriggerTags(extractTagList(answers, "mood_trigger_tags"));

            try {
                entry.setAnswersJson(objectMapper.writeValueAsString(answers));
            } catch (JsonProcessingException ex) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("success", false, "message", "Could not save check-in payload."));
            }

            try {
                quickCheckinEntryRepository.save(entry);
            } catch (Exception ex) {
                log.error("Failed to save quick check-in entry", ex);
                try {
                    entry.setAnswersJson(objectMapper.writeValueAsString(buildCompactAnswers(answers)));
                    quickCheckinEntryRepository.save(entry);
                } catch (JsonProcessingException retryEx) {
                    log.error("Retry with compact quick check-in payload also failed", retryEx);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of(
                                    "success", false,
                                    "message", "We could not save your check-in right now. Please try again."));
                }
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("saved", true);
            response.put("id", entry.getId());
            response.put("savedAt", entry.getCreatedAt().toString());
            response.put("moodLabel", entry.getMoodLabel());
            response.put("confidence", entry.getResponseConfidence());
            response.put("averageScore", entry.getAverageScore());
            response.put("lowSignalCount", entry.getLowSignalCount());
            response.put("answers", answers);
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            log.error("Unexpected error while processing quick check-in payload", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", "Unexpected issue while saving this check-in. Please try once more."));
        }
    }

    @GetMapping("/latest")
    public ResponseEntity<Map<String, Object>> latestQuickCheckin(HttpSession session) {
        Optional<User> userOpt = getCurrentUser(session);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("saved", false, "message", "Login required"));
        }

        Optional<QuickCheckinEntry> entryOpt = quickCheckinEntryRepository
                .findTopByUserOrderByCreatedAtDesc(userOpt.get());

        if (entryOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of("saved", false));
        }

        QuickCheckinEntry entry = entryOpt.get();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("saved", true);
        response.put("id", entry.getId());
        response.put("savedAt", entry.getCreatedAt().toString());
        response.put("version", entry.getQuestionnaireVersion());
        response.put("moodLabel", entry.getMoodLabel());
        response.put("confidence", entry.getResponseConfidence());
        response.put("averageScore", entry.getAverageScore());
        response.put("lowSignalCount", entry.getLowSignalCount());
        response.put("moodTriggerTags", entry.getMoodTriggerTags() == null ? List.of() : entry.getMoodTriggerTags());
        response.put("answers", parseAnswersJson(entry.getAnswersJson()));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/weekly-summary")
    public ResponseEntity<Map<String, Object>> weeklySummary(HttpSession session) {
        Optional<User> userOpt = getCurrentUser(session);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("hasData", false, "message", "Login required"));
        }

        if (!userOpt.get().isDataAnalysisEnabled()) {
            return ResponseEntity.ok(Map.of(
                    "analysisDisabled", true,
                    "hasData", false,
                    "message", "Data analysis permission is off. Enable it in Profile > Other Settings."));
        }

        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDateTime start = weekStart.atStartOfDay();
        LocalDateTime end = LocalDateTime.now();

        List<QuickCheckinEntry> entries = quickCheckinEntryRepository
                .findByUserAndCreatedAtBetweenOrderByCreatedAtAsc(userOpt.get(), start, end);

        if (entries.isEmpty()) {
            return ResponseEntity.ok(Map.of("hasData", false));
        }

        double avgScore = entries.stream().mapToDouble(QuickCheckinEntry::getAverageScore).average().orElse(0.0);
        double avgConfidence = entries.stream().mapToInt(QuickCheckinEntry::getResponseConfidence).average().orElse(0.0);
        double avgLowSignals = entries.stream().mapToInt(QuickCheckinEntry::getLowSignalCount).average().orElse(0.0);
        long highStrainChecks = entries.stream()
                .filter(e -> e.getAverageScore() < 2.6 || e.getLowSignalCount() >= 4)
                .count();

        double firstScore = entries.get(0).getAverageScore();
        double lastScore = entries.get(entries.size() - 1).getAverageScore();
        double delta = round2(lastScore - firstScore);
        String trend = "steady";
        if (delta >= 0.35) {
            trend = "improving";
        } else if (delta <= -0.35) {
            trend = "dropping";
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("hasData", true);
        response.put("checkinsCount", entries.size());
        response.put("averageScore", round2(avgScore));
        response.put("averageConfidence", Math.round(avgConfidence));
        response.put("averageLowSignals", round2(avgLowSignals));
        response.put("highStrainCheckins", highStrainChecks);
        response.put("trend", trend);
        response.put("scoreDelta", delta);
        return ResponseEntity.ok(response);
    }

    private Optional<User> getCurrentUser(HttpSession session) {
        String email = session == null ? null : (String) session.getAttribute("email");
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmail(email);
    }

    private Map<String, Object> normalizeAnswerMap(Object raw) {
        if (!(raw instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = String.valueOf(entry.getKey()).trim();
            if (!key.isEmpty()) {
                normalized.put(key, entry.getValue());
            }
        }
        return normalized;
    }

    private int countAnswered(Map<String, Object> answers) {
        int count = 0;
        for (Object answer : answers.values()) {
            if (answer instanceof Map<?, ?> mapValue) {
                Object score = mapValue.get("score");
                Object text = mapValue.get("text");
                if (parseScaleScore(score) != null || !sanitize(text, "", 600).isBlank()) {
                    count++;
                }
                continue;
            }
            if (!sanitize(answer, "", 600).isBlank()) {
                count++;
            }
        }
        return count;
    }

    private int parseQuestionCount(Object raw) {
        Integer parsed = parsePositiveInt(raw);
        if (parsed == null || parsed < 1 || parsed > 40) {
            return DEFAULT_QUESTION_COUNT;
        }
        return parsed;
    }

    private Integer extractScore(Map<String, Object> answers, String key) {
        Object answer = answers.get(key);
        if (answer instanceof Map<?, ?> mapValue) {
            return parseScaleScore(mapValue.get("score"));
        }
        return null;
    }

    private String extractText(Map<String, Object> answers, String key) {
        Object answer = answers.get(key);
        if (answer instanceof Map<?, ?> mapValue) {
            return sanitize(mapValue.get("text"), "", 600);
        }
        return sanitize(answer, "", 600);
    }

    private List<String> extractTagList(Map<String, Object> answers, String key) {
        Object answer = answers.get(key);
        if (!(answer instanceof Map<?, ?> mapValue)) {
            return List.of();
        }

        Object tagsRaw = mapValue.get("tags");
        if (!(tagsRaw instanceof List<?> tagsList)) {
            return List.of();
        }

        List<String> tags = new ArrayList<>();
        for (Object raw : tagsList) {
            String tag = sanitize(raw, "", 120);
            if (!tag.isBlank() && !tags.contains(tag)) {
                tags.add(tag);
            }
        }
        return tags;
    }

    private Integer parseScaleScore(Object raw) {
        if (raw instanceof Number number) {
            int value = number.intValue();
            if (value >= 1 && value <= 5) {
                return value;
            }
            return null;
        }
        if (raw instanceof String value) {
            try {
                int parsed = Integer.parseInt(value.trim());
                if (parsed >= 1 && parsed <= 5) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer parsePositiveInt(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String value) {
            try {
                return Integer.valueOf(value.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer firstPresentScore(Integer... values) {
        if (values == null || values.length == 0) {
            return 3;
        }
        for (Integer value : values) {
            if (value != null) {
                return value;
            }
        }
        return 3;
    }

    private List<Integer> collectNonNullScores(Integer... values) {
        List<Integer> scores = new ArrayList<>();
        if (values == null || values.length == 0) {
            return scores;
        }
        for (Integer value : values) {
            if (value != null) {
                scores.add(value);
            }
        }
        return scores;
    }

    private Map<String, Object> buildCompactAnswers(Map<String, Object> answers) {
        Map<String, Object> compact = new LinkedHashMap<>();
        if (answers == null || answers.isEmpty()) {
            return compact;
        }

        int index = 1;
        for (Map.Entry<String, Object> entry : answers.entrySet()) {
            String compactKey = "q" + index++;
            Object rawAnswer = entry.getValue();

            if (rawAnswer instanceof Map<?, ?> answerMap) {
                Integer score = parseScaleScore(answerMap.get("score"));
                if (score != null) {
                    compact.put(compactKey, score);
                    continue;
                }

                Object tagKeys = answerMap.get("selectedTagKeys");
                if (tagKeys instanceof List<?> list && !list.isEmpty()) {
                    String joined = list.stream()
                            .map(item -> sanitize(item, "", 16))
                            .filter(value -> !value.isBlank())
                            .limit(8)
                            .reduce((a, b) -> a + "|" + b)
                            .orElse("");
                    if (!joined.isBlank()) {
                        compact.put(compactKey, joined);
                        continue;
                    }
                }

                String text = sanitize(answerMap.get("text"), "", 40);
                if (!text.isBlank()) {
                    compact.put(compactKey, text);
                    continue;
                }

                String otherText = sanitize(answerMap.get("otherText"), "", 30);
                compact.put(compactKey, otherText.isBlank() ? "1" : otherText);
                continue;
            }

            String text = sanitize(rawAnswer, "", 40);
            compact.put(compactKey, text.isBlank() ? "1" : text);
        }

        return compact;
    }

    private String deriveMoodLabel(double averageScore, int lowSignalCount) {
        if (lowSignalCount >= 5 || averageScore <= 1.8) {
            return "High Strain";
        }
        if (lowSignalCount >= 3 || averageScore <= 2.6) {
            return "Needs Support";
        }
        if (averageScore <= 3.3) {
            return "Mixed";
        }
        if (averageScore <= 4.1) {
            return "Steady";
        }
        return "Resilient";
    }

    private Map<String, Object> parseAnswersJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String sanitize(Object raw, String fallback, int maxLength) {
        String value = raw == null ? "" : String.valueOf(raw).trim().replaceAll("\\s+", " ");
        if (value.isBlank()) {
            return fallback;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
