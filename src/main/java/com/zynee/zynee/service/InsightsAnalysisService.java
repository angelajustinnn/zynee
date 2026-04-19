package com.zynee.zynee.service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zynee.zynee.model.JournalEntry;
import com.zynee.zynee.model.MoodLog;
import com.zynee.zynee.model.QuickCheckinEntry;
import com.zynee.zynee.model.User;

@Service
public class InsightsAnalysisService {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final String insightsUrl;

    public InsightsAnalysisService(
            ObjectMapper objectMapper,
            @Value("${assistant.python.insights-url:http://127.0.0.1:8001/insights-analyze}") String insightsUrl,
            @Value("${assistant.python.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${assistant.python.read-timeout-ms:30000}") int readTimeoutMs) {
        this.objectMapper = objectMapper;
        this.insightsUrl = insightsUrl == null ? "" : insightsUrl.trim();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(connectTimeoutMs, 1000));
        requestFactory.setReadTimeout(Math.max(readTimeoutMs, 1000));
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public Map<String, Object> analyze(
            User user,
            List<MoodLog> moodLogs,
            List<JournalEntry> journalEntries,
            List<QuickCheckinEntry> quickCheckins) {
        if (insightsUrl.isBlank()) {
            return buildFallbackSummary("assistant.python.insights-url is not configured.", moodLogs, journalEntries, quickCheckins);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("sunSign", deriveSunSign(user == null ? null : user.getDob()));
        payload.put("countryCode", user == null ? "" : safeText(user.getCountryCode()));
        payload.put("moodLogs", toMoodPayload(moodLogs));
        payload.put("journalEntries", toJournalPayload(journalEntries));
        payload.put("quickCheckins", toQuickCheckinPayload(quickCheckins));

        try {
            String requestBody = objectMapper.writeValueAsString(payload);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(insightsUrl, HttpMethod.POST, requestEntity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                return buildFallbackSummary(
                        "Insights microservice error (" + response.getStatusCode().value() + ").",
                        moodLogs,
                        journalEntries,
                        quickCheckins);
            }
            String body = response.getBody();
            if (body == null || body.isBlank()) {
                return buildFallbackSummary("Insights microservice returned empty body.", moodLogs, journalEntries, quickCheckins);
            }

            Map<String, Object> parsed = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {
            });
            return parsed == null ? buildFallbackSummary("Insights microservice returned invalid data.", moodLogs, journalEntries, quickCheckins) : parsed;
        } catch (HttpStatusCodeException ex) {
            return buildFallbackSummary(
                    "Insights microservice error (" + ex.getStatusCode().value() + ").",
                    moodLogs,
                    journalEntries,
                    quickCheckins);
        } catch (ResourceAccessException ex) {
            return buildFallbackSummary(
                    "Could not reach local insights microservice at " + insightsUrl,
                    moodLogs,
                    journalEntries,
                    quickCheckins);
        } catch (RestClientException | IOException ex) {
            return buildFallbackSummary(
                    "Failed to analyze insights via microservice: " + ex.getMessage(),
                    moodLogs,
                    journalEntries,
                    quickCheckins);
        }
    }

    private List<Map<String, Object>> toMoodPayload(List<MoodLog> logs) {
        List<MoodLog> safe = logs == null ? List.of() : logs;
        return safe.stream()
                .filter(item -> item != null && item.getTimestamp() != null)
                .sorted(Comparator.comparing(MoodLog::getTimestamp))
                .skip(Math.max(0, safe.size() - 240))
                .map(item -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("moodLevel", clampInt(item.getMoodLevel(), 1, 5));
                    row.put("timestamp", item.getTimestamp().toString());
                    row.put("feelings", item.getFeelings() == null ? List.of() : new ArrayList<>(item.getFeelings()));
                    row.put("triggerTags", item.getTriggerTags() == null ? List.of() : new ArrayList<>(item.getTriggerTags()));
                    return row;
                })
                .toList();
    }

    private List<Map<String, Object>> toJournalPayload(List<JournalEntry> entries) {
        List<JournalEntry> safe = entries == null ? List.of() : entries;
        return safe.stream()
                .filter(item -> item != null && item.getContent() != null && !item.getContent().isBlank())
                .sorted(Comparator.comparing(JournalEntry::getDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(JournalEntry::getTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .skip(Math.max(0, safe.size() - 240))
                .map(item -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("content", trimLength(item.getContent(), 5000));
                    row.put("date", item.getDate() == null ? "" : item.getDate().toString());
                    row.put("time", item.getTime() == null ? "" : item.getTime().toString());
                    return row;
                })
                .toList();
    }

    private List<Map<String, Object>> toQuickCheckinPayload(List<QuickCheckinEntry> entries) {
        List<QuickCheckinEntry> safe = entries == null ? List.of() : entries;
        return safe.stream()
                .filter(item -> item != null && item.getCreatedAt() != null)
                .sorted(Comparator.comparing(QuickCheckinEntry::getCreatedAt))
                .skip(Math.max(0, safe.size() - 120))
                .map(item -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("createdAt", item.getCreatedAt().toString());
                    row.put("averageScore", item.getAverageScore());
                    row.put("moodLabel", safeText(item.getMoodLabel()));
                    row.put("lowSignalCount", item.getLowSignalCount());
                    row.put("responseConfidence", item.getResponseConfidence());
                    row.put("stressScore", item.getStressScore());
                    row.put("anxietyScore", item.getAnxietyScore());
                    row.put("energyScore", item.getEnergyScore());
                    row.put("sleepScore", item.getSleepScore());
                    row.put("motivationScore", item.getMotivationScore());
                    row.put("socialScore", item.getSocialScore());
                    row.put("hopeScore", item.getHopeScore());
                    row.put("supportScore", item.getSupportScore());
                    row.put("primaryTrigger", safeText(item.getPrimaryTrigger()));
                    row.put("moodTriggerTags", item.getMoodTriggerTags() == null ? List.of() : new ArrayList<>(item.getMoodTriggerTags()));
                    return row;
                })
                .toList();
    }

    private Map<String, Object> buildFallbackSummary(
            String reason,
            List<MoodLog> moodLogs,
            List<JournalEntry> journalEntries,
            List<QuickCheckinEntry> quickCheckins) {
        int moodCount = moodLogs == null ? 0 : moodLogs.size();
        int journalCount = journalEntries == null ? 0 : journalEntries.size();
        int quickCount = quickCheckins == null ? 0 : quickCheckins.size();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("generatedAt", java.time.OffsetDateTime.now().toString());
        summary.put("forecast", Map.of(
                "todayMood", "Calm",
                "todayConfidence", 35,
                "tomorrowMood", "Calm",
                "tomorrowConfidence", 33,
                "logsUsed", Math.max(0, moodCount)));
        summary.put("statsMetrics", Map.of(
                "stressIndex", 50,
                "emotionalStability", 50,
                "positivityRatio", 50,
                "confidence", 40));
        summary.put("moodPattern", Map.of(
                "dominantMoodPattern", "Not enough data yet",
                "mostLikelyTrigger", "Collect more mood/check-in entries",
                "bestTimeMoodWindow", "Unknown",
                "riskWindow", "Unknown",
                "trendStrength", "Insufficient data",
                "triggerConfidence", "40%",
                "personalizationScore", "35%"));
        summary.put("weeklyWellness", Map.of(
                "overallWeeklyCheckin", "We need a few more entries to personalize this report.",
                "energyTrend", "Unknown",
                "stressSnapshot", "Unknown",
                "recoveryIndicator", "Unknown",
                "winsThisWeek", "Add 2-3 entries for better tracking",
                "focusAreaForNextWeek", "Consistency",
                "reportConfidence", "35%"));
        summary.put("cosmicInsights", Map.of(
                "sunSign", "Unknown",
                "mood", "Collect more mood logs for cosmic blending.",
                "emotion", "Journal cues are still sparse.",
                "feeling", "Neutral baseline",
                "guidance", "Add more daily entries and this card will personalize automatically.",
                "dailyVibe", "Your emotional tone is still forming, so log a quick mood check to personalize this.",
                "dailyContext", "Your feelings are still calibrating today, so a few more mood and journal signals will improve your vibe reading.",
                "tomorrowVibe", "Start with a short check-in and a journal note.",
                "confidence", "35%",
                "source", "fallback"));
        summary.put("dataPoints", Map.of(
                "moodLogs", moodCount,
                "journalEntries", journalCount,
                "quickCheckins", quickCount));
        summary.put("fallbackReason", reason);
        return summary;
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimLength(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim().replaceAll("\\s+", " ");
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    private String deriveSunSign(LocalDate dob) {
        if (dob == null) {
            return "Unknown";
        }
        MonthDay md = MonthDay.from(dob);
        if (!md.isBefore(MonthDay.of(3, 21)) && !md.isAfter(MonthDay.of(4, 19))) return "Aries";
        if (!md.isBefore(MonthDay.of(4, 20)) && !md.isAfter(MonthDay.of(5, 20))) return "Taurus";
        if (!md.isBefore(MonthDay.of(5, 21)) && !md.isAfter(MonthDay.of(6, 20))) return "Gemini";
        if (!md.isBefore(MonthDay.of(6, 21)) && !md.isAfter(MonthDay.of(7, 22))) return "Cancer";
        if (!md.isBefore(MonthDay.of(7, 23)) && !md.isAfter(MonthDay.of(8, 22))) return "Leo";
        if (!md.isBefore(MonthDay.of(8, 23)) && !md.isAfter(MonthDay.of(9, 22))) return "Virgo";
        if (!md.isBefore(MonthDay.of(9, 23)) && !md.isAfter(MonthDay.of(10, 22))) return "Libra";
        if (!md.isBefore(MonthDay.of(10, 23)) && !md.isAfter(MonthDay.of(11, 21))) return "Scorpio";
        if (!md.isBefore(MonthDay.of(11, 22)) && !md.isAfter(MonthDay.of(12, 21))) return "Sagittarius";
        if (!md.isBefore(MonthDay.of(12, 22)) || !md.isAfter(MonthDay.of(1, 19))) return "Capricorn";
        if (!md.isBefore(MonthDay.of(1, 20)) && !md.isAfter(MonthDay.of(2, 18))) return "Aquarius";
        if (!md.isBefore(MonthDay.of(2, 19)) && !md.isAfter(MonthDay.of(3, 20))) return "Pisces";
        return "Unknown";
    }
}
