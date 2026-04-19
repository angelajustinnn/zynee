package com.zynee.zynee.controller;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zynee.zynee.model.JournalEntry;
import com.zynee.zynee.model.MoodLog;
import com.zynee.zynee.model.QuickCheckinEntry;
import com.zynee.zynee.model.User;
import com.zynee.zynee.repository.JournalEntryRepository;
import com.zynee.zynee.repository.MoodLogRepository;
import com.zynee.zynee.repository.QuickCheckinEntryRepository;
import com.zynee.zynee.repository.UserRepository;
import com.zynee.zynee.service.InsightsAnalysisService;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api")
public class MoodForecastController {

    private final UserRepository userRepository;
    private final MoodLogRepository moodLogRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final QuickCheckinEntryRepository quickCheckinEntryRepository;
    private final InsightsAnalysisService insightsAnalysisService;

    public MoodForecastController(
            UserRepository userRepository,
            MoodLogRepository moodLogRepository,
            JournalEntryRepository journalEntryRepository,
            QuickCheckinEntryRepository quickCheckinEntryRepository,
            InsightsAnalysisService insightsAnalysisService) {
        this.userRepository = userRepository;
        this.moodLogRepository = moodLogRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.quickCheckinEntryRepository = quickCheckinEntryRepository;
        this.insightsAnalysisService = insightsAnalysisService;
    }

    @GetMapping("/mood-forecast")
    public ResponseEntity<Map<String, Object>> getMoodForecast(HttpSession session) {
        String email = (String) session.getAttribute("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Please log in to see your mood forecast."));
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not found."));
        }

        User user = userOpt.get();
        if (!user.isDataAnalysisEnabled()) {
            return ResponseEntity.ok(Map.of(
                    "analysisDisabled", true,
                    "forecastAvailable", false,
                    "message", "Data analysis permission is off. Enable it in Profile > Other Settings."));
        }

        List<MoodLog> moodLogs = moodLogRepository.findByUser(user).stream()
                .filter(item -> item != null && item.getTimestamp() != null)
                .sorted(Comparator.comparing(MoodLog::getTimestamp))
                .toList();
        List<JournalEntry> journalEntries = journalEntryRepository.findByUserOrderByDateDescTimeDesc(user).stream()
                .filter(item -> item != null && item.getContent() != null && !item.getContent().isBlank())
                .toList();
        List<QuickCheckinEntry> quickCheckins = quickCheckinEntryRepository.findTop20ByUserOrderByCreatedAtDesc(user).stream()
                .filter(item -> item != null && item.getCreatedAt() != null)
                .toList();

        boolean hasAnySignal = !moodLogs.isEmpty() || !journalEntries.isEmpty() || !quickCheckins.isEmpty();
        if (!hasAnySignal) {
            return ResponseEntity.ok(Map.of(
                    "forecastAvailable", false,
                    "message", "Add mood, journal, or quick check-in entries to unlock forecasting."));
        }

        Map<String, Object> insights = insightsAnalysisService.analyze(user, moodLogs, journalEntries, quickCheckins);
        Object forecastObj = insights.get("forecast");
        if (!(forecastObj instanceof Map<?, ?> rawForecast)) {
            return ResponseEntity.ok(Map.of(
                    "forecastAvailable", false,
                    "message", "Forecast data is still calibrating. Please try again shortly."));
        }

        String todayMood = asText(rawForecast.get("todayMood"), "Calm");
        int todayConfidence = clampConfidence(asInt(rawForecast.get("todayConfidence"), 40));
        String tomorrowMood = asText(rawForecast.get("tomorrowMood"), todayMood);
        int tomorrowConfidence = clampConfidence(asInt(rawForecast.get("tomorrowConfidence"), Math.max(30, todayConfidence - 4)));
        int logsUsed = Math.max(0, asInt(rawForecast.get("logsUsed"), moodLogs.size()));
        String source = asText(rawForecast.get("source"), "insights-hybrid");

        return ResponseEntity.ok(Map.of(
                "forecastAvailable", true,
                "todayMood", todayMood,
                "todayConfidence", todayConfidence,
                "tomorrowMood", tomorrowMood,
                "tomorrowConfidence", tomorrowConfidence,
                "source", source,
                "logsUsed", logsUsed,
                "signalCounts", Map.of(
                        "moodLogs", moodLogs.size(),
                        "journalEntries", journalEntries.size(),
                        "quickCheckins", quickCheckins.size())));
    }

    private String asText(Object value, String fallback) {
        String raw = value == null ? "" : String.valueOf(value).trim();
        return raw.isEmpty() ? fallback : raw;
    }

    private int asInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private int clampConfidence(int value) {
        return Math.max(30, Math.min(96, value));
    }
}
