package com.zynee.zynee.controller;

import java.time.LocalDate;
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
@RequestMapping("/api/insights")
public class InsightsApiController {

    private final UserRepository userRepository;
    private final MoodLogRepository moodLogRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final QuickCheckinEntryRepository quickCheckinEntryRepository;
    private final InsightsAnalysisService insightsAnalysisService;

    public InsightsApiController(
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

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(HttpSession session) {
        String email = (String) session.getAttribute("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Please log in to view your insights."));
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not found."));
        }

        User user = userOpt.get();
        if (!user.isDataAnalysisEnabled()) {
            clearInsightsSummarySessionCache(session);
            return ResponseEntity.ok(Map.of(
                    "analysisDisabled", true,
                    "hasData", false,
                    "noData", true,
                    "error", "Data analysis permission is off. Enable it in Profile > Other Settings.",
                    "message", "Analysis disabled by your preference."));
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
            clearInsightsSummarySessionCache(session);
            return ResponseEntity.ok(Map.of(
                    "hasData", false,
                    "noData", true,
                    "error", "No logs yet. Add mood, journal, or quick check-in entries to unlock insights."));
        }

        String moodLatest = moodLogs.isEmpty() ? "-" : String.valueOf(moodLogs.get(moodLogs.size() - 1).getTimestamp());
        String journalLatest = journalEntries.isEmpty()
                ? "-"
                : (String.valueOf(journalEntries.get(0).getDate()) + "T" + String.valueOf(journalEntries.get(0).getTime()));
        String quickLatest = quickCheckins.isEmpty() ? "-" : String.valueOf(quickCheckins.get(0).getCreatedAt());

        String todayKey = LocalDate.now().toString();
        String fingerprint = String.join("|",
                String.valueOf(moodLogs.size()),
                moodLatest,
                String.valueOf(journalEntries.size()),
                journalLatest,
                String.valueOf(quickCheckins.size()),
                quickLatest);

        Object cachedDate = session.getAttribute("insightsSummaryDate");
        Object cachedEmail = session.getAttribute("insightsSummaryEmail");
        Object cachedFingerprint = session.getAttribute("insightsSummaryFingerprint");
        Object cachedPayload = session.getAttribute("insightsSummaryData");
        if (todayKey.equals(cachedDate)
                && email.equals(cachedEmail)
                && fingerprint.equals(cachedFingerprint)
                && cachedPayload instanceof Map<?, ?> rawCached) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cached = (Map<String, Object>) rawCached;
            return ResponseEntity.ok(cached);
        }

        Map<String, Object> result = insightsAnalysisService.analyze(user, moodLogs, journalEntries, quickCheckins);
        session.setAttribute("insightsSummaryDate", todayKey);
        session.setAttribute("insightsSummaryEmail", email);
        session.setAttribute("insightsSummaryFingerprint", fingerprint);
        session.setAttribute("insightsSummaryData", result);
        return ResponseEntity.ok(result);
    }

    private void clearInsightsSummarySessionCache(HttpSession session) {
        if (session == null) {
            return;
        }
        session.removeAttribute("insightsSummaryDate");
        session.removeAttribute("insightsSummaryEmail");
        session.removeAttribute("insightsSummaryFingerprint");
        session.removeAttribute("insightsSummaryData");
    }
}
