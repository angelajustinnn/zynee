package com.zynee.zynee.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.lang.reflect.Proxy;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zynee.zynee.model.Affirmation;
import com.zynee.zynee.model.FutureNote;
import com.zynee.zynee.model.JournalEntry;
import com.zynee.zynee.model.MoodLog;
import com.zynee.zynee.model.QuickCheckinEntry;
import com.zynee.zynee.model.SavedAffirmation;
import com.zynee.zynee.model.User;
import com.zynee.zynee.repository.FutureNoteRepository;
import com.zynee.zynee.repository.JournalEntryRepository;
import com.zynee.zynee.repository.MoodLogRepository;
import com.zynee.zynee.repository.QuickCheckinEntryRepository;
import com.zynee.zynee.repository.SavedAffirmationRepository;
import com.zynee.zynee.repository.UserRepository;

class LoginControllerExportDataPdfTest {

    @Test
    void exportDataGeneratesReadablePdfWithUserData() throws Exception {
        LoginController controller = new LoginController();

        String email = "pdf-test@zynee.local";
        User user = new User();
        user.setName("PDF Test User");
        user.setEmail(email);

        MoodLog moodLog = new MoodLog();
        moodLog.setTimestamp(LocalDateTime.of(2026, 4, 10, 9, 30));
        moodLog.setMoodLevel(4);
        moodLog.setFeelings(List.of("calm", "grateful"));
        moodLog.setTriggerTags(List.of("work"));

        JournalEntry journalEntry = new JournalEntry();
        journalEntry.setDate(LocalDate.of(2026, 4, 11));
        journalEntry.setTime(LocalTime.of(20, 15));
        journalEntry.setContent("Today was better than yesterday 😊 and I finished my goals.");

        QuickCheckinEntry quickCheckin = new QuickCheckinEntry();
        quickCheckin.setCreatedAt(LocalDateTime.of(2026, 4, 12, 18, 10));
        quickCheckin.setAverageScore(4.2);
        quickCheckin.setMoodLabel("Good");
        quickCheckin.setResponseConfidence(88);
        quickCheckin.setLowSignalCount(0);
        quickCheckin.setMoodTriggerTags(List.of("exam pressure", "sleep"));
        quickCheckin.setAnswersJson("{}");

        FutureNote futureNote = new FutureNote();
        futureNote.setId(1L);
        futureNote.setCreatedAt(LocalDateTime.of(2026, 4, 8, 11, 0));
        futureNote.setContent("Stay consistent this month.");

        Affirmation affirmation = new Affirmation();
        affirmation.setMessage("I am resilient.");
        SavedAffirmation savedAffirmation = new SavedAffirmation();
        savedAffirmation.setAffirmation(affirmation);
        savedAffirmation.setSavedAt(LocalDateTime.of(2026, 4, 12, 8, 0));

        UserRepository userRepository = repo(
                UserRepository.class,
                Map.of("findByEmail", Optional.of(user)));
        MoodLogRepository moodLogRepository = repo(
                MoodLogRepository.class,
                Map.of("findByUserEmail", List.of(moodLog)));
        JournalEntryRepository journalEntryRepository = repo(
                JournalEntryRepository.class,
                Map.of("findByUserEmail", List.of(journalEntry)));
        SavedAffirmationRepository savedAffirmationRepository = repo(
                SavedAffirmationRepository.class,
                Map.of("findByUserEmail", List.of(savedAffirmation)));
        FutureNoteRepository futureNoteRepository = repo(
                FutureNoteRepository.class,
                Map.of("findByUserEmail", List.of(futureNote)));
        QuickCheckinEntryRepository quickCheckinEntryRepository = repo(
                QuickCheckinEntryRepository.class,
                Map.of("findByUserEmail", List.of(quickCheckin)));

        ReflectionTestUtils.setField(controller, "userRepository", userRepository);
        ReflectionTestUtils.setField(controller, "moodLogRepository", moodLogRepository);
        ReflectionTestUtils.setField(controller, "journalEntryRepository", journalEntryRepository);
        ReflectionTestUtils.setField(controller, "savedAffirmationRepository", savedAffirmationRepository);
        ReflectionTestUtils.setField(controller, "futureNoteRepository", futureNoteRepository);
        ReflectionTestUtils.setField(controller, "quickCheckinEntryRepository", quickCheckinEntryRepository);
        ReflectionTestUtils.setField(controller, "objectMapper", new ObjectMapper());

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("email", email);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.exportUserData(response, session);

        assertEquals(200, response.getStatus());
        assertEquals("application/pdf", response.getContentType());

        byte[] pdfBytes = response.getContentAsByteArray();
        assertTrue(pdfBytes.length > 100, "Expected non-empty PDF");
        String header = new String(pdfBytes, 0, Math.min(5, pdfBytes.length), StandardCharsets.ISO_8859_1);
        assertTrue(header.startsWith("%PDF-"), "Expected PDF header");
        String tail = new String(pdfBytes, Math.max(0, pdfBytes.length - 2048), Math.min(2048, pdfBytes.length), StandardCharsets.ISO_8859_1);
        assertTrue(tail.contains("xref"));
        assertTrue(tail.contains("trailer"));
        assertTrue(tail.contains("%%EOF"));

        try (PDDocument pdf = PDDocument.load(pdfBytes)) {
            assertTrue(pdf.getNumberOfPages() >= 1, "Expected at least one page");
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("Journey Overview"));
            assertTrue(text.contains("Stats, Insights & Mood Analysis"));
            assertTrue(text.contains("Quick Check-In Reports & Weekly Pattern Signals"));
            assertTrue(text.contains("Journal Activity Analysis"));
            assertTrue(text.contains("Future Notes Timeline"));
            assertTrue(text.contains("Affirmation Usage"));
            assertTrue(text.contains("Detailed Log History"));
            assertTrue(text.contains("Mood 5/5"));
            assertTrue(text.contains("I am resilient."));
        }
    }

    @Test
    void exportDataWithLargeDatasetStillProducesCompletePdf() throws Exception {
        LoginController controller = new LoginController();

        String email = "pdf-load-test@zynee.local";
        User user = new User();
        user.setName("PDF Load User");
        user.setEmail(email);

        List<MoodLog> moodLogs = new ArrayList<>();
        for (int i = 0; i < 220; i++) {
            MoodLog moodLog = new MoodLog();
            moodLog.setTimestamp(LocalDateTime.of(2026, 4, 1, 8, 0).plusHours(i));
            moodLog.setMoodLevel((i % 5) + 1);
            moodLog.setFeelings(List.of("focused", "grateful"));
            moodLog.setTriggerTags(List.of("work", "sleep"));
            moodLogs.add(moodLog);
        }

        List<JournalEntry> journalEntries = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            JournalEntry entry = new JournalEntry();
            entry.setDate(LocalDate.of(2026, 1, 1).plusDays(i));
            entry.setTime(LocalTime.of(20, 15));
            entry.setContent("Long entry " + i + " - building consistency and reflection through daily writing.");
            journalEntries.add(entry);
        }

        List<QuickCheckinEntry> quickCheckins = new ArrayList<>();
        for (int i = 0; i < 140; i++) {
            QuickCheckinEntry checkin = new QuickCheckinEntry();
            checkin.setCreatedAt(LocalDateTime.of(2026, 2, 1, 10, 0).plusDays(i));
            checkin.setAverageScore(3.5 + ((i % 3) * 0.3));
            checkin.setMoodLabel("Good");
            checkin.setResponseConfidence(80);
            checkin.setLowSignalCount(i % 2);
            checkin.setMoodTriggerTags(List.of("exams", "sleep"));
            checkin.setAnswersJson("{}");
            quickCheckins.add(checkin);
        }

        FutureNote futureNote = new FutureNote();
        futureNote.setId(1L);
        futureNote.setCreatedAt(LocalDateTime.of(2026, 3, 8, 11, 0));
        futureNote.setContent("Keep going.");

        Affirmation affirmation = new Affirmation();
        affirmation.setMessage("I can do hard things.");
        SavedAffirmation savedAffirmation = new SavedAffirmation();
        savedAffirmation.setAffirmation(affirmation);
        savedAffirmation.setSavedAt(LocalDateTime.of(2026, 4, 12, 8, 0));

        UserRepository userRepository = repo(
                UserRepository.class,
                Map.of("findByEmail", Optional.of(user)));
        MoodLogRepository moodLogRepository = repo(
                MoodLogRepository.class,
                Map.of("findByUserEmail", moodLogs));
        JournalEntryRepository journalEntryRepository = repo(
                JournalEntryRepository.class,
                Map.of("findByUserEmail", journalEntries));
        SavedAffirmationRepository savedAffirmationRepository = repo(
                SavedAffirmationRepository.class,
                Map.of("findByUserEmail", List.of(savedAffirmation)));
        FutureNoteRepository futureNoteRepository = repo(
                FutureNoteRepository.class,
                Map.of("findByUserEmail", List.of(futureNote)));
        QuickCheckinEntryRepository quickCheckinEntryRepository = repo(
                QuickCheckinEntryRepository.class,
                Map.of("findByUserEmail", quickCheckins));

        ReflectionTestUtils.setField(controller, "userRepository", userRepository);
        ReflectionTestUtils.setField(controller, "moodLogRepository", moodLogRepository);
        ReflectionTestUtils.setField(controller, "journalEntryRepository", journalEntryRepository);
        ReflectionTestUtils.setField(controller, "savedAffirmationRepository", savedAffirmationRepository);
        ReflectionTestUtils.setField(controller, "futureNoteRepository", futureNoteRepository);
        ReflectionTestUtils.setField(controller, "quickCheckinEntryRepository", quickCheckinEntryRepository);
        ReflectionTestUtils.setField(controller, "objectMapper", new ObjectMapper());

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("email", email);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.exportUserData(response, session);

        assertEquals(200, response.getStatus());
        assertEquals("application/pdf", response.getContentType());
        byte[] pdfBytes = response.getContentAsByteArray();
        assertTrue(pdfBytes.length > 10_000, "Expected large multi-page PDF");
        String tail = new String(pdfBytes, Math.max(0, pdfBytes.length - 2048), Math.min(2048, pdfBytes.length), StandardCharsets.ISO_8859_1);
        assertTrue(tail.contains("xref"));
        assertTrue(tail.contains("trailer"));
        assertTrue(tail.contains("%%EOF"));

        try (PDDocument pdf = PDDocument.load(pdfBytes)) {
            assertTrue(pdf.getNumberOfPages() >= 3, "Expected multiple pages");
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("Detailed Log History"));
            assertTrue(text.contains("Journal Entries:"));
            assertTrue(text.contains("Quick Check-Ins:"));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T repo(Class<T> repoType, Map<String, Object> methodReturns) {
        return (T) Proxy.newProxyInstance(
                repoType.getClassLoader(),
                new Class<?>[] { repoType },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("toString".equals(name)) {
                        return repoType.getSimpleName() + "Proxy";
                    }
                    if ("hashCode".equals(name)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(name)) {
                        return proxy == args[0];
                    }
                    if (methodReturns.containsKey(name)) {
                        return methodReturns.get(name);
                    }
                    throw new UnsupportedOperationException("Unexpected repository call: " + repoType.getSimpleName() + "." + name);
                });
    }
}
