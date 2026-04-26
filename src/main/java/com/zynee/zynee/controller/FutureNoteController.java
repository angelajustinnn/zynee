package com.zynee.zynee.controller;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import com.zynee.zynee.model.FutureNote;
import com.zynee.zynee.model.User;
import com.zynee.zynee.repository.FutureNoteRepository;
import com.zynee.zynee.repository.UserRepository;

import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

@Controller
public class FutureNoteController {

    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private final UserRepository userRepository;
    private final FutureNoteRepository futureNoteRepository;

    public FutureNoteController(UserRepository userRepository, FutureNoteRepository futureNoteRepository) {
        this.userRepository = userRepository;
        this.futureNoteRepository = futureNoteRepository;
    }

    @GetMapping({"/future-notes", "/future-notes.html"})
    public String futureNotesPage() {
        return "future-notes.html";
    }

    @PostMapping("/api/future-notes")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createFutureNote(@RequestBody Map<String, String> payload, HttpSession session) {
        Optional<User> userOpt = getCurrentUser(session);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in first."));
        }

        String content = safeTrim(payload.get("content"));
        String unlockAtRaw = safeTrim(payload.get("unlockAt"));

        if (content.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please write your future note before saving."));
        }
        if (content.length() > 3000) {
            return ResponseEntity.badRequest().body(Map.of("error", "Future note is too long. Keep it under 3000 characters."));
        }
        if (unlockAtRaw.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please choose unlock date and time."));
        }

        final LocalDateTime unlockAt;
        try {
            unlockAt = parseIncomingDateTime(unlockAtRaw);
        } catch (DateTimeParseException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid unlock date/time format."));
        }

        if (!unlockAt.isAfter(nowUtc())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Unlock date/time must be in the future so your note stays sealed."));
        }

        FutureNote note = new FutureNote();
        note.setUser(userOpt.get());
        note.setContent(content);
        note.setCreatedAt(nowUtc());
        note.setUnlockAt(unlockAt);
        futureNoteRepository.save(note);

        return ResponseEntity.ok(Map.of("message", "Future note sealed and added to your jar."));
    }

    @GetMapping("/api/future-notes")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> listFutureNotes(HttpSession session) {
        Optional<User> userOpt = getCurrentUser(session);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in first."));
        }

        User user = userOpt.get();
        LocalDateTime now = nowUtc();
        List<FutureNote> notes = futureNoteRepository.findByUserOrderByUnlockAtAscCreatedAtAsc(user);

        List<Map<String, Object>> allNotes = notes.stream()
                .map(note -> toViewMap(note, now))
                .toList();

        List<Map<String, Object>> unlockedNotes = notes.stream()
                .filter(note -> !note.getUnlockAt().isAfter(now))
                .map(note -> toUnlockedMap(note))
                .toList();

        long unreadUnlockedCount = futureNoteRepository.countByUserAndUnlockAtLessThanEqualAndOpenedAtIsNull(user, now);
        long lockedCount = notes.stream().filter(note -> note.getUnlockAt().isAfter(now)).count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("allNotes", allNotes);
        result.put("unlockedNotes", unlockedNotes);
        result.put("lockedCount", lockedCount);
        result.put("unlockedCount", unlockedNotes.size());
        result.put("unreadUnlockedCount", unreadUnlockedCount);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/future-notes/acknowledge")
    @Transactional
    @ResponseBody
    public ResponseEntity<Map<String, Object>> acknowledgeUnlocked(HttpSession session) {
        Optional<User> userOpt = getCurrentUser(session);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in first."));
        }

        User user = userOpt.get();
        LocalDateTime now = nowUtc();
        List<FutureNote> unreadUnlocked = futureNoteRepository
                .findByUserAndUnlockAtLessThanEqualAndOpenedAtIsNull(user, now);
        for (FutureNote note : unreadUnlocked) {
            note.setOpenedAt(now);
        }
        if (!unreadUnlocked.isEmpty()) {
            futureNoteRepository.saveAll(unreadUnlocked);
        }

        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/api/future-notes/{id}")
    @Transactional
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteFutureNote(@PathVariable("id") Long id, HttpSession session) {
        Optional<User> userOpt = getCurrentUser(session);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in first."));
        }

        return deleteFutureNoteForUser(userOpt.get(), id);
    }

    @PostMapping("/api/future-notes/delete")
    @Transactional
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteFutureNotePost(@RequestBody Map<String, Object> payload, HttpSession session) {
        Optional<User> userOpt = getCurrentUser(session);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in first."));
        }

        Object idValue = payload.get("id");
        if (idValue == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing note id."));
        }

        final Long id;
        try {
            id = Long.valueOf(String.valueOf(idValue));
        } catch (NumberFormatException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid note id."));
        }

        return deleteFutureNoteForUser(userOpt.get(), id);
    }

    @PostMapping("/api/future-notes/{id}/delete")
    @Transactional
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteFutureNoteByPathPost(@PathVariable("id") Long id, HttpSession session) {
        Optional<User> userOpt = getCurrentUser(session);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in first."));
        }
        return deleteFutureNoteForUser(userOpt.get(), id);
    }

    @GetMapping("/api/future-notes/notification")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> notificationSummary(HttpSession session) {
        Optional<User> userOpt = getCurrentUser(session);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in first."));
        }

        User user = userOpt.get();
        LocalDateTime now = nowUtc();
        long unreadUnlockedCount = futureNoteRepository.countByUserAndUnlockAtLessThanEqualAndOpenedAtIsNull(user, now);
        return ResponseEntity.ok(Map.of(
                "unreadUnlockedCount", unreadUnlockedCount,
                "hasUnlocked", unreadUnlockedCount > 0
        ));
    }

    private Optional<User> getCurrentUser(HttpSession session) {
        String email = (String) session.getAttribute("email");
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmail(email);
    }

    private Map<String, Object> toViewMap(FutureNote note, LocalDateTime now) {
        boolean locked = note.getUnlockAt().isAfter(now);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", note.getId());
        view.put("locked", locked);
        view.put("writtenAt", formatDateTime(note.getCreatedAt()));
        view.put("writtenAtIso", toIsoUtc(note.getCreatedAt()));
        view.put("unlockAt", formatDateTime(note.getUnlockAt()));
        view.put("unlockAtIso", toIsoUtc(note.getUnlockAt()));
        view.put("status", locked ? "sealed" : "unlocked");
        view.put("content", locked ? "" : note.getContent());
        return view;
    }

    private Map<String, Object> toUnlockedMap(FutureNote note) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", note.getId());
        view.put("writtenAt", formatDateTime(note.getCreatedAt()));
        view.put("writtenAtIso", toIsoUtc(note.getCreatedAt()));
        view.put("unlockAt", formatDateTime(note.getUnlockAt()));
        view.put("unlockAtIso", toIsoUtc(note.getUnlockAt()));
        view.put("content", note.getContent());
        return view;
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DISPLAY_FORMATTER);
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private String toIsoUtc(LocalDateTime value) {
        if (value == null) return "";
        return value.atOffset(ZoneOffset.UTC).toInstant().toString();
    }

    private LocalDateTime parseIncomingDateTime(String rawValue) {
        try {
            return LocalDateTime.parse(rawValue);
        } catch (DateTimeParseException ignored) {
            return OffsetDateTime.parse(rawValue)
                    .withOffsetSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        }
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private ResponseEntity<Map<String, Object>> deleteFutureNoteForUser(User user, Long id) {
        Long userId = user.getId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Future note not found."));
        }

        long deletedCount = futureNoteRepository.deleteByIdAndUser_Id(id, userId);
        if (deletedCount <= 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Future note not found."));
        }
        futureNoteRepository.flush();
        return ResponseEntity.ok(Map.of("success", true, "message", "Future note deleted."));
    }
}
