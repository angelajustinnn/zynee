// LoginController.java

package com.zynee.zynee.controller;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zynee.zynee.model.FutureNote;
import com.zynee.zynee.model.JournalEntry;
import com.zynee.zynee.model.MoodLog;
import com.zynee.zynee.model.QuickCheckinEntry;
import com.zynee.zynee.model.SavedAffirmation;
import com.zynee.zynee.model.User;
import com.zynee.zynee.repository.FutureNoteRepository;
import com.zynee.zynee.repository.JournalEntryRepository;
import com.zynee.zynee.repository.MoodLogRepository;
import com.zynee.zynee.repository.MusicTrackRepository;
import com.zynee.zynee.repository.QuickCheckinEntryRepository;
import com.zynee.zynee.repository.SavedAffirmationRepository;
import com.zynee.zynee.repository.UserRepository;
import com.zynee.zynee.service.GuestSessionService;
import com.zynee.zynee.service.MailService;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

@Controller
public class LoginController {

    @Autowired
private MailService mailService;

    

    @Autowired
private MoodLogRepository moodLogRepository;

    @Autowired
private JournalEntryRepository journalEntryRepository;

@PostMapping("/save-journal")
@ResponseBody
    @SuppressWarnings("CallToPrintStackTrace")
public String saveJournalEntry(@RequestParam("content") String content,
                               @RequestParam("date") String date,
                               @RequestParam("time") String time,
                               HttpSession session) {
    String email = (String) session.getAttribute("email");
    if (email == null) return "error";

    Optional<User> userOpt = userRepository.findByEmail(email);
    if (userOpt.isEmpty()) return "error";

    User user = userOpt.get();

    try {
        JournalEntry entry = new JournalEntry();
        entry.setUser(user);
        entry.setContent(content);
        entry.setDate(LocalDate.parse(date));
        entry.setTime(LocalTime.parse(time)); // ✅ supports "HH:mm"

        journalEntryRepository.save(entry);
        return "success";
    } catch (Exception e) {
        e.printStackTrace();
        return "error: " + e.getMessage();
    }
}
    @Autowired
    private UserRepository userRepository;

@Autowired
private SavedAffirmationRepository savedAffirmationRepository;

@Autowired
private FutureNoteRepository futureNoteRepository;

@Autowired
private QuickCheckinEntryRepository quickCheckinEntryRepository;

@Autowired
private ObjectMapper objectMapper;

@Autowired
private GuestSessionService guestSessionService;

    @Autowired
    private MusicTrackRepository musicTrackRepository;

    private static final String JOURNAL_PIN_VERIFIED_SESSION_KEY = "journalPinVerified";
    private static final String JOURNAL_PIN_VERIFIED_HASH_SESSION_KEY = "journalPinVerifiedHash";
    private static final int JOURNAL_PIN_MAX_ATTEMPTS = 3;
    private static final int JOURNAL_PIN_BASE_LOCK_MINUTES = 15;
    private static final int JOURNAL_PIN_LOCK_STEP_MINUTES = 5;

    private Optional<User> getCurrentUser(HttpSession session) {
        String email = (String) session.getAttribute("email");
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmail(email);
    }

    private boolean isJournalPinSet(User user) {
        return user.getJournalPinHash() != null && !user.getJournalPinHash().isBlank();
    }

    private boolean isJournalPinVerified(HttpSession session, User user) {
        if (!Boolean.TRUE.equals(session.getAttribute(JOURNAL_PIN_VERIFIED_SESSION_KEY))) {
            return false;
        }
        String verifiedHash = (String) session.getAttribute(JOURNAL_PIN_VERIFIED_HASH_SESSION_KEY);
        String currentHash = user.getJournalPinHash();
        return currentHash != null && currentHash.equals(verifiedHash);
    }

    private int getJournalPinFailedAttempts(User user) {
        Integer attempts = user.getJournalPinFailedAttempts();
        return attempts == null ? 0 : attempts;
    }

    private int getJournalPinLockLevel(User user) {
        Integer lockLevel = user.getJournalPinLockLevel();
        return lockLevel != null ? lockLevel : 0;
    }

    private int getNextJournalPinLockMinutes(User user) {
        return JOURNAL_PIN_BASE_LOCK_MINUTES + (getJournalPinLockLevel(user) * JOURNAL_PIN_LOCK_STEP_MINUTES);
    }

    private boolean isJournalPinLocked(User user) {
        LocalDateTime lockUntil = user.getJournalPinLockUntil();
        return lockUntil != null && lockUntil.isAfter(LocalDateTime.now());
    }

    private long getJournalPinLockRemainingSeconds(User user) {
        LocalDateTime lockUntil = user.getJournalPinLockUntil();
        if (lockUntil == null) return 0;
        long seconds = ChronoUnit.SECONDS.between(LocalDateTime.now(), lockUntil);
        return Math.max(0, seconds);
    }

    private boolean clearExpiredJournalPinLock(User user) {
        if (user.getJournalPinLockUntil() != null && !user.getJournalPinLockUntil().isAfter(LocalDateTime.now())) {
            user.setJournalPinLockUntil(null);
            return true;
        }
        return false;
    }

    private boolean isAllowedPinLength(Integer pinLength) {
        return pinLength != null && (pinLength == 4 || pinLength == 6);
    }

    private boolean isDigitPinOfLength(String pin, int length) {
        return pin != null && pin.matches("\\d{" + length + "}");
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    @SuppressWarnings("UnnecessaryTemporaryOnConversionFromString")
    private Integer safeParsePinLength(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException | NullPointerException ex) {
            return null;
        }
    }

    private int normalizeMoodLevel(int rawMoodLevel) {
        // Backward compatibility:
        // older UI values were 0..4, newer ML-friendly scale is 1..5.
        if (rawMoodLevel >= 0 && rawMoodLevel <= 4) {
            return rawMoodLevel + 1;
        }
        return Math.max(1, Math.min(5, rawMoodLevel));
    }

    private List<String> normalizeTextList(List<String> values, int maxItems, int maxLength) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        List<String> normalized = new ArrayList<>();
        for (String raw : values) {
            if (raw == null) {
                continue;
            }
            String value = raw.trim().replaceAll("\\s+", " ");
            if (value.isBlank()) {
                continue;
            }
            if (value.length() > maxLength) {
                value = value.substring(0, maxLength);
            }
            if (!normalized.contains(value)) {
                normalized.add(value);
            }
            if (normalized.size() >= maxItems) {
                break;
            }
        }
        return normalized;
    }

    private void clearUserForecastAndPin(User user, HttpSession session) {
        user.setMoodForecastDate(null);
        user.setTodayMoodPrediction(null);
        user.setTodayMoodConfidence(null);
        user.setTomorrowMoodPrediction(null);
        user.setTomorrowMoodConfidence(null);
        user.setJournalPinHash(null);
        user.setJournalPinLength(null);
        user.setJournalPinFailedAttempts(0);
        user.setJournalPinLockUntil(null);
        user.setJournalPinLockLevel(0);
        if (session != null) {
            session.removeAttribute(JOURNAL_PIN_VERIFIED_SESSION_KEY);
            session.removeAttribute(JOURNAL_PIN_VERIFIED_HASH_SESSION_KEY);
            session.removeAttribute("insightsSummaryDate");
            session.removeAttribute("insightsSummaryEmail");
            session.removeAttribute("insightsSummaryFingerprint");
            session.removeAttribute("insightsSummaryData");
        }
    }

    private void resetJournalPinFailures(User user) {
        user.setJournalPinFailedAttempts(0);
        user.setJournalPinLockUntil(null);
        user.setJournalPinLockLevel(0);
    }

    private Map<String, Object> buildJournalPinStatus(User user, HttpSession session) {
        Map<String, Object> status = new LinkedHashMap<>();
        boolean locked = isJournalPinLocked(user);
        int failedAttempts = getJournalPinFailedAttempts(user);
        status.put("authorized", true);
        status.put("pinSet", isJournalPinSet(user));
        status.put("pinLength", user.getJournalPinLength());
        status.put("verified", isJournalPinVerified(session, user));
        status.put("locked", locked);
        status.put("lockRemainingSeconds", locked ? getJournalPinLockRemainingSeconds(user) : 0);
        status.put("failedAttempts", failedAttempts);
        status.put("remainingAttempts", Math.max(0, JOURNAL_PIN_MAX_ATTEMPTS - failedAttempts));
        return status;
    }

    private Map<String, Object> onJournalPinFailure(User user, HttpSession session) {
        Map<String, Object> result = new LinkedHashMap<>();
        int failedAttempts = getJournalPinFailedAttempts(user) + 1;
        user.setJournalPinFailedAttempts(failedAttempts);
        session.removeAttribute(JOURNAL_PIN_VERIFIED_SESSION_KEY);
        session.removeAttribute(JOURNAL_PIN_VERIFIED_HASH_SESSION_KEY);

        result.put("success", false);
        result.put("authorized", true);

        if (failedAttempts >= JOURNAL_PIN_MAX_ATTEMPTS) {
            int lockMinutes = getNextJournalPinLockMinutes(user);
            user.setJournalPinFailedAttempts(0);
            user.setJournalPinLockUntil(LocalDateTime.now().plusMinutes(lockMinutes));
            user.setJournalPinLockLevel(getJournalPinLockLevel(user) + 1);
            userRepository.save(user);

            result.put("message", "Too many wrong PIN attempts. Try again later.");
            result.put("locked", true);
            result.put("lockRemainingSeconds", getJournalPinLockRemainingSeconds(user));
            result.put("nextLockMinutes", lockMinutes);
            return result;
        }

        userRepository.save(user);
        result.put("message", "Incorrect PIN.");
        result.put("locked", false);
        result.put("remainingAttempts", JOURNAL_PIN_MAX_ATTEMPTS - failedAttempts);
        return result;
    }

    private Map<String, Object> onJournalPinSuccess(User user, HttpSession session, String message) {
        resetJournalPinFailures(user);
        userRepository.save(user);
        session.setAttribute(JOURNAL_PIN_VERIFIED_SESSION_KEY, true);
        session.setAttribute(JOURNAL_PIN_VERIFIED_HASH_SESSION_KEY, user.getJournalPinHash());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("authorized", true);
        result.put("message", message);
        result.putAll(buildJournalPinStatus(user, session));
        return result;
    }

    private Map<String, Object> lockedPinResponse(User user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("authorized", true);
        result.put("locked", true);
        result.put("message", "PIN is temporarily locked. Please wait before trying again.");
        result.put("lockRemainingSeconds", getJournalPinLockRemainingSeconds(user));
        return result;
    }

    // ===================== LOGIN =====================
   @GetMapping("/login.html")
    public String showLoginPage(@RequestParam(required = false) String error,
                                @RequestParam(required = false) String logout,
                                @RequestParam(required = false) String reauth,
                                @RequestParam(required = false) String signup,
                                HttpServletRequest request,
                                Model model) {
        HttpSession existingSession = request.getSession(false);
        if (existingSession != null && Boolean.TRUE.equals(existingSession.getAttribute("isGuest"))) {
            existingSession.invalidate();
        }

        if (error != null) model.addAttribute("error", error);
        if ("success".equalsIgnoreCase(signup)) model.addAttribute("message", "Signup successful. Please log in.");
        if (logout != null) model.addAttribute("message", "You have been logged out.");
        if (reauth != null) model.addAttribute("message", "Please log in again to continue.");
        return "login.html";
    }

@PostMapping("/login.html")
public String handleLogin(@RequestParam("email") String email,
                          @RequestParam("password") String password,
                          HttpServletRequest request,
                          HttpSession session,
                          Model model) {
    Optional<User> userOpt = userRepository.findByEmail(email);
    if (userOpt.isEmpty() || !BCrypt.checkpw(password, userOpt.get().getPassword())) {
        model.addAttribute("error", "Invalid login credentials");
        return "login.html";
    }

    User user = userOpt.get();
    String newOtp = String.format("%06d", new Random().nextInt(999999));

    session.setAttribute("otp", newOtp);
    session.setAttribute("otpTime", System.currentTimeMillis());
    session.setAttribute("expectedOtp", newOtp);
    session.setAttribute("otpTimestamp", LocalDateTime.now());
    session.setAttribute("lastResendTime", LocalDateTime.now());
    session.setAttribute("resendCount", 0);

    // Keep user pending until OTP verifies
    session.setAttribute("pendingUserId", user.getId());
    session.setAttribute("pendingEmail", user.getEmail());

    mailService.sendOtpEmail(user.getEmail(), newOtp);

    return "redirect:/otp-verify";
}
    
    @GetMapping("/api/stats-data")
@ResponseBody
public Map<String, Object> getWeeklyStats(HttpSession session) {
    String email = (String) session.getAttribute("email");
    if (email == null) return Map.of("error", "unauthorized");

    Optional<User> userOpt = userRepository.findByEmail(email);
    if (userOpt.isEmpty()) return Map.of("error", "unauthorized");

    User user = userOpt.get();

    LocalDate today = LocalDate.now();
    LocalDate weekStart = today.with(java.time.DayOfWeek.MONDAY);   // Start of this week
   // LocalDate weekStart = today.minusDays(6);
    LocalDateTime startOfWeek = weekStart.atStartOfDay();
    LocalDateTime endOfWeek = today.atTime(23, 59, 59);

    // Mood Log: count how many of each mood_level this week
    List<MoodLog> moodLogs = moodLogRepository.findByUserAndTimestampBetween(user, startOfWeek, endOfWeek);
    System.out.println("📊 Logged in user: " + email);
System.out.println("⏰ Weekly range: " + startOfWeek + " to " + endOfWeek);
System.out.println("🎯 Mood logs fetched: " + moodLogs.size());
for (MoodLog log : moodLogs) {
    System.out.println("  ➤ Mood Level: " + log.getMoodLevel() + ", Timestamp: " + log.getTimestamp());
}

    int[] moodCounts = new int[5]; // index 0=very sad ... 4=very happy
    for (MoodLog log : moodLogs) {
        int normalizedLevel = normalizeMoodLevel(log.getMoodLevel());
        int chartIndex = normalizedLevel - 1;
        if (chartIndex >= 0 && chartIndex < moodCounts.length) {
            moodCounts[chartIndex]++;
        }
    }
    // ➕ Build a map of day name → list of feelings
Map<String, List<String>> dailyFeelings = new LinkedHashMap<>();
for (int i = 0; i < 7; i++) {
    LocalDate date = weekStart.plusDays(i);
    String dayName = date.getDayOfWeek().toString().substring(0, 1).toUpperCase()
                      + date.getDayOfWeek().toString().substring(1).toLowerCase(); // e.g., "Monday"
    dailyFeelings.put(dayName, new ArrayList<>());
}

// Populate the map with tags from mood logs
for (MoodLog log : moodLogs) {
    LocalDate logDate = log.getTimestamp().toLocalDate();
    String dayName = logDate.getDayOfWeek().toString().substring(0, 1).toUpperCase()
                     + logDate.getDayOfWeek().toString().substring(1).toLowerCase();
    List<String> tags = log.getFeelings(); // assuming this is a List<String>
    if (tags != null && !tags.isEmpty()) {
        dailyFeelings.get(dayName).addAll(tags);
    }
}

    // Journal Entries per day (Mon-Sun)
    List<JournalEntry> entries = journalEntryRepository.findByUserAndDateBetween(user, weekStart, today);
    Map<String, Integer> dailyEntries = new java.util.LinkedHashMap<>();
    for (int i = 0; i < 7; i++) {
        LocalDate date = weekStart.plusDays(i);
        String day = date.getDayOfWeek().toString().substring(0, 3); // e.g., "Mon"
        dailyEntries.put(day, 0);
    }

    for (JournalEntry entry : entries) {
        String day = entry.getDate().getDayOfWeek().toString().substring(0, 3);
        dailyEntries.computeIfPresent(day, (k, v) -> v + 1);
    }

    // Quick Check-In entries per day (Mon-Sun)
    List<QuickCheckinEntry> quickCheckins = quickCheckinEntryRepository
            .findByUserAndCreatedAtBetweenOrderByCreatedAtAsc(user, startOfWeek, endOfWeek);
    Map<String, Integer> dailyQuickCheckins = new LinkedHashMap<>();
    for (int i = 0; i < 7; i++) {
        LocalDate date = weekStart.plusDays(i);
        String day = date.getDayOfWeek().toString().substring(0, 3);
        dailyQuickCheckins.put(day, 0);
    }

    for (QuickCheckinEntry entry : quickCheckins) {
        if (entry.getCreatedAt() == null) {
            continue;
        }
        String day = entry.getCreatedAt().toLocalDate().getDayOfWeek().toString().substring(0, 3);
        dailyQuickCheckins.computeIfPresent(day, (k, v) -> v + 1);
    }

    return Map.of(
    "moodCounts", moodCounts,
    "journalCounts", dailyEntries.values(),
    "quickCheckinCounts", dailyQuickCheckins.values(),
    "dailyFeelings", dailyFeelings
);
}
@GetMapping("/view-entries")
public String showViewEntriesPage(HttpSession session) {
    Optional<User> userOpt = getCurrentUser(session);
    if (userOpt.isEmpty()) {
        return "redirect:/login.html?reauth=true";
    }

    User user = userOpt.get();
    if (clearExpiredJournalPinLock(user)) {
        userRepository.save(user);
    }

    if (isJournalPinSet(user) && !isJournalPinVerified(session, user)) {
        return "redirect:/journal.html?pinRequired=true";
    }

    return "view-entries"; // This refers to templates/view-entries.html
}

@GetMapping("/api/journal-pin/status")
@ResponseBody
public Map<String, Object> getJournalPinStatus(HttpSession session) {
    Optional<User> userOpt = getCurrentUser(session);
    if (userOpt.isEmpty()) {
        return Map.of("authorized", false, "message", "Login required");
    }

    User user = userOpt.get();
    if (clearExpiredJournalPinLock(user)) {
        userRepository.save(user);
    }

    return buildJournalPinStatus(user, session);
}

@PostMapping("/api/journal-pin/setup")
@ResponseBody
public Map<String, Object> setupJournalPin(@RequestBody Map<String, String> payload, HttpSession session) {
    Optional<User> userOpt = getCurrentUser(session);
    if (userOpt.isEmpty()) {
        return Map.of("success", false, "authorized", false, "message", "Login required");
    }

    User user = userOpt.get();
    if (isJournalPinSet(user)) {
        return Map.of("success", false, "authorized", true, "message", "PIN already set. Use change PIN.");
    }

    Integer pinLength = safeParsePinLength(payload.get("pinLength"));
    String pin = safeTrim(payload.get("pin"));
    String confirmPin = safeTrim(payload.get("confirmPin"));

    if (!isAllowedPinLength(pinLength)) {
        return Map.of("success", false, "authorized", true, "message", "Choose a 4 or 6 digit PIN.");
    }
    if (!isDigitPinOfLength(pin, pinLength)) {
        return Map.of("success", false, "authorized", true, "message", "PIN format is invalid.");
    }
    if (!pin.equals(confirmPin)) {
        return Map.of("success", false, "authorized", true, "message", "PIN and confirmation do not match.");
    }

    user.setJournalPinHash(BCrypt.hashpw(pin, BCrypt.gensalt()));
    user.setJournalPinLength(pinLength);
    resetJournalPinFailures(user);
    userRepository.save(user);
    session.removeAttribute(JOURNAL_PIN_VERIFIED_SESSION_KEY);
    session.removeAttribute(JOURNAL_PIN_VERIFIED_HASH_SESSION_KEY);

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("success", true);
    response.put("authorized", true);
    response.put("message", "Journal PIN created.");
    response.putAll(buildJournalPinStatus(user, session));
    return response;
}

@PostMapping("/api/journal-pin/verify")
@ResponseBody
public Map<String, Object> verifyJournalPin(@RequestBody Map<String, String> payload, HttpSession session) {
    Optional<User> userOpt = getCurrentUser(session);
    if (userOpt.isEmpty()) {
        return Map.of("success", false, "authorized", false, "message", "Login required");
    }

    User user = userOpt.get();
    if (clearExpiredJournalPinLock(user)) {
        userRepository.save(user);
    }

    if (!isJournalPinSet(user)) {
        session.setAttribute(JOURNAL_PIN_VERIFIED_SESSION_KEY, true);
        session.setAttribute(JOURNAL_PIN_VERIFIED_HASH_SESSION_KEY, user.getJournalPinHash());
        return Map.of("success", true, "authorized", true, "message", "PIN is not set.");
    }

    if (isJournalPinLocked(user)) {
        return lockedPinResponse(user);
    }

    String pin = safeTrim(payload.get("pin"));
    Integer pinLength = user.getJournalPinLength();
    if (!isAllowedPinLength(pinLength) || !isDigitPinOfLength(pin, pinLength)) {
        return onJournalPinFailure(user, session);
    }

    if (!BCrypt.checkpw(pin, user.getJournalPinHash())) {
        return onJournalPinFailure(user, session);
    }

    return onJournalPinSuccess(user, session, "PIN verified.");
}

@PostMapping("/api/journal-pin/change")
@ResponseBody
public Map<String, Object> changeJournalPin(@RequestBody Map<String, String> payload, HttpSession session) {
    Optional<User> userOpt = getCurrentUser(session);
    if (userOpt.isEmpty()) {
        return Map.of("success", false, "authorized", false, "message", "Login required");
    }

    User user = userOpt.get();
    if (!isJournalPinSet(user)) {
        return Map.of("success", false, "authorized", true, "message", "No PIN is set yet.");
    }

    if (clearExpiredJournalPinLock(user)) {
        userRepository.save(user);
    }
    if (isJournalPinLocked(user)) {
        return lockedPinResponse(user);
    }

    String currentPin = safeTrim(payload.get("currentPin"));
    if (!BCrypt.checkpw(currentPin, user.getJournalPinHash())) {
        return onJournalPinFailure(user, session);
    }

    Integer pinLength = safeParsePinLength(payload.get("pinLength"));
    String newPin = safeTrim(payload.get("newPin"));
    String confirmPin = safeTrim(payload.get("confirmPin"));

    if (!isAllowedPinLength(pinLength)) {
        return Map.of("success", false, "authorized", true, "message", "Choose a 4 or 6 digit PIN.");
    }
    if (!isDigitPinOfLength(newPin, pinLength)) {
        return Map.of("success", false, "authorized", true, "message", "New PIN format is invalid.");
    }
    if (!newPin.equals(confirmPin)) {
        return Map.of("success", false, "authorized", true, "message", "New PIN and confirmation do not match.");
    }

    user.setJournalPinHash(BCrypt.hashpw(newPin, BCrypt.gensalt()));
    user.setJournalPinLength(pinLength);
    return onJournalPinSuccess(user, session, "PIN changed successfully.");
}

@PostMapping("/api/journal-pin/remove")
@ResponseBody
public Map<String, Object> removeJournalPin(@RequestBody Map<String, String> payload, HttpSession session) {
    Optional<User> userOpt = getCurrentUser(session);
    if (userOpt.isEmpty()) {
        return Map.of("success", false, "authorized", false, "message", "Login required");
    }

    User user = userOpt.get();
    if (!isJournalPinSet(user)) {
        return Map.of("success", false, "authorized", true, "message", "No PIN is set.");
    }

    if (clearExpiredJournalPinLock(user)) {
        userRepository.save(user);
    }
    if (isJournalPinLocked(user)) {
        return lockedPinResponse(user);
    }

    String currentPin = safeTrim(payload.get("currentPin"));
    if (!BCrypt.checkpw(currentPin, user.getJournalPinHash())) {
        return onJournalPinFailure(user, session);
    }

    user.setJournalPinHash(null);
    user.setJournalPinLength(null);
    resetJournalPinFailures(user);
    userRepository.save(user);
    session.removeAttribute(JOURNAL_PIN_VERIFIED_SESSION_KEY);
    session.removeAttribute(JOURNAL_PIN_VERIFIED_HASH_SESSION_KEY);

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("success", true);
    response.put("authorized", true);
    response.put("message", "Journal PIN removed.");
    response.putAll(buildJournalPinStatus(user, session));
    return response;
}

@GetMapping("/api/journal-entries")
@ResponseBody
public org.springframework.http.ResponseEntity<?> getUserJournalEntries(HttpSession session) {
    Optional<User> userOpt = getCurrentUser(session);
    if (userOpt.isEmpty()) {
        return org.springframework.http.ResponseEntity.status(401)
                .body(Map.of("error", "unauthorized"));
    }

    User user = userOpt.get();
    if (clearExpiredJournalPinLock(user)) {
        userRepository.save(user);
    }
    if (isJournalPinSet(user) && !isJournalPinVerified(session, user)) {
        String error = isJournalPinLocked(user) ? "pin_locked" : "pin_required";
        return org.springframework.http.ResponseEntity.status(423)
                .body(Map.of(
                        "error", error,
                        "lockRemainingSeconds", getJournalPinLockRemainingSeconds(user)
                ));
    }

    List<JournalEntry> entries = journalEntryRepository.findByUserOrderByDateDescTimeDesc(user);

    List<Map<String, String>> result = new ArrayList<>();
    for (JournalEntry entry : entries) {
        result.add(Map.of(
            "id", entry.getId().toString(),
            "content", entry.getContent(),
            "date", entry.getDate().toString(),
            "time", entry.getTime().toString()
        ));
    }

    return org.springframework.http.ResponseEntity.ok(result);
}

@PostMapping("/api/delete-journal-entry")
@ResponseBody
public String deleteJournalEntry(@RequestParam Long id, HttpSession session) {
    Optional<User> userOpt = getCurrentUser(session);
    if (userOpt.isEmpty()) return "unauthorized";

    User user = userOpt.get();
    if (clearExpiredJournalPinLock(user)) {
        userRepository.save(user);
    }
    if (isJournalPinSet(user) && !isJournalPinVerified(session, user)) {
        return isJournalPinLocked(user) ? "pin_locked" : "pin_required";
    }

    Optional<JournalEntry> entryOpt = journalEntryRepository.findById(id);

    if (entryOpt.isPresent() && entryOpt.get().getUser().getId().equals(user.getId())) {
        journalEntryRepository.deleteById(id);
        return "success";
    }

    return "not_found";
}

    @PostMapping("/save-mood")
@ResponseBody
public String saveMood(@RequestParam int moodLevel,
                       @RequestParam(value = "feelings", required = false) List<String> feelings,
                       @RequestParam(value = "triggers", required = false) List<String> triggers,
                       HttpSession session) {
    String email = (String) session.getAttribute("email");
    if (email == null) return "error";

    Optional<User> userOpt = userRepository.findByEmail(email);
    if (userOpt.isEmpty()) return "error";

    User user = userOpt.get();

    MoodLog moodLog = new MoodLog();
    moodLog.setUser(user);
    moodLog.setMoodLevel(normalizeMoodLevel(moodLevel));
    moodLog.setFeelings(normalizeTextList(feelings, 50, 80));
    moodLog.setTriggerTags(normalizeTextList(triggers, 20, 120));
    moodLog.setTimestamp(LocalDateTime.now());

    moodLogRepository.save(moodLog);

    return "success";
}

    // ===================== UPLOAD PROFILE PHOTO =====================
    @PostMapping("/upload-profile-photo")
    @ResponseBody
    @SuppressWarnings({"UseSpecificCatch", "CallToPrintStackTrace"})
    public String uploadProfilePhoto(@RequestParam("profilePhoto") MultipartFile file,
                                     HttpSession session) {
        try {
            System.out.println("📥 Received upload request");

            if (file.isEmpty()) {
                System.out.println("⚠️ File is empty");
                return "No file uploaded";
            }

            String email = (String) session.getAttribute("email");
            if (email == null) {
                System.out.println("⚠️ No session email");
                return "User not logged in";
            }

            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return "User not found";
            }

            User user = userOpt.get();
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.contains(".")) {
                return "Invalid file format";
            }
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));

            String uniqueFileName = UUID.randomUUID().toString() + extension;
            Path uploadPath = Paths.get(System.getProperty("user.dir"), "uploads", "profile-pics");
            System.out.println("📂 Saving to: " + uploadPath.toAbsolutePath());
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            Path filePath = uploadPath.resolve(uniqueFileName);
            file.transferTo(filePath.toFile());

            String photoPath = "/profile-pics/" + uniqueFileName;
            user.setProfilePhoto(photoPath);
            userRepository.save(user);
            session.setAttribute("profilePhoto", photoPath);

            return "Success: Image uploaded";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    @PostMapping("/remove-profile-photo")
@ResponseBody
    @SuppressWarnings("CallToPrintStackTrace")
public String removeProfilePhoto(HttpSession session) {
    String existingPath = (String) session.getAttribute("profilePhoto");

    if (existingPath != null && !existingPath.isEmpty()) {
        Path filePath = Paths.get("uploads", "profile-pics", existingPath.substring(existingPath.lastIndexOf('/') + 1));

        try {
            Files.deleteIfExists(filePath);
            session.removeAttribute("profilePhoto");

            // Update user in DB
            User user = (User) session.getAttribute("user");
            if (user != null) {
                user.setProfilePhoto(null);
                userRepository.save(user); // or your DAO update
            }

            return "Success";
        } catch (IOException e) {
            e.printStackTrace();
            return "Failed to delete photo";
        }
    }

    return "No photo found";
}

    // ===================== SIGNUP =====================
    @GetMapping("/signup.html")
    public String showSignupPage(@RequestParam(required = false) String error, Model model) {
        if (error != null) model.addAttribute("error", error);
        return "signup.html";
    }

    @GetMapping("/terms")
    public String termsPage() {
        return "terms";
    }

    @PostMapping("/update-profile")
    public String updateProfile(@RequestParam(required = false) String name,
                                @RequestParam String phone,
                                @RequestParam String countryCode,
                                @RequestParam String gender,
                                @RequestParam String dob,
                                HttpSession session,
                                Model model) {
        String email = (String) session.getAttribute("email");
        if (email == null) return "redirect:/login.html";

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();

            LocalDate today = LocalDate.now();
            LocalDate lastEdit = user.getLastNameChangeDate();
            long daysSince = lastEdit != null ? ChronoUnit.DAYS.between(lastEdit, today) : 15;

            boolean canEdit = daysSince >= 14;
            if (canEdit && !user.getName().equals(name)) {
                user.setName(name);
                user.setLastNameChangeDate(today);
                session.setAttribute("name", name);
            }

            user.setPhone(countryCode + phone);
            user.setCountryCode(countryCode);
            user.setGender(gender);
            LocalDate parsedDob = null;
            if (dob != null && !dob.isBlank()) {
                parsedDob = LocalDate.parse(dob);
                user.setDob(parsedDob);
            }
            userRepository.save(user);

            session.setAttribute("countryCode", countryCode);
            session.setAttribute("phone", phone);
            session.setAttribute("gender", gender);
            session.setAttribute("dob", parsedDob);
            model.addAttribute("saveSuccess", true);
        }

        return showProfilePage(session, model);
    }

    // ===================== LOGOUT =====================
@GetMapping("/logout")
public String logout(HttpSession session, HttpServletResponse response) {
    session.invalidate();

    response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
    response.setHeader("Pragma", "no-cache");
    response.setDateHeader("Expires", 0);
    response.setHeader("Set-Cookie", "JSESSIONID=; Path=/; HttpOnly; Max-Age=0");
    response.setHeader("Clear-Site-Data", "\"cache\", \"cookies\", \"storage\"");

    return "redirect:/login.html?logout=true";
}

    @GetMapping("/")
    public String redirectToHome() {
        return "redirect:/home.html";
    }

@PostMapping("/verify-otp")
public String verifyOtp(@RequestParam("otp") String otp,
                        HttpServletRequest request,
                        HttpServletResponse response,
                        HttpSession session,
                        Model model) {

    String expectedOtp = (String) session.getAttribute("expectedOtp");
    Long pendingUserId = (Long) session.getAttribute("pendingUserId");

    if (expectedOtp == null || pendingUserId == null) {
        return "redirect:/login.html";
    }

    if (!otp.equals(expectedOtp)) {
        model.addAttribute("otpError", "Invalid OTP. Please try again.");
        return "otp-verify";
    }

    Optional<User> userOpt = userRepository.findById(pendingUserId);
    if (userOpt.isEmpty()) {
        session.invalidate();
        return "redirect:/login.html";
    }

    setupSessionAfterLogin(userOpt.get(), session);

    // Clear OTP/pending state after successful verification
    session.removeAttribute("otp");
    session.removeAttribute("otpTime");
    session.removeAttribute("expectedOtp");
    session.removeAttribute("otpTimestamp");
    session.removeAttribute("lastResendTime");
    session.removeAttribute("resendCount");
    session.removeAttribute("pendingUserId");
    session.removeAttribute("pendingEmail");

    return "redirect:/home.html";
}

@GetMapping("/otp-verify")
public String showOtpVerifyPage(@RequestParam(required = false) String error, Model model) {
    if (error != null) model.addAttribute("error", error);
    return "otp-verify"; // ✅ must match the name of your Thymeleaf file: otp-verify.html
}

@GetMapping("/resend-otp")
public String resendOtp(HttpSession session, Model model) {
    String pendingEmail = (String) session.getAttribute("pendingEmail");
    if (pendingEmail == null) {
        return "redirect:/login.html";
    }

    Integer resendCount = (Integer) session.getAttribute("resendCount");
    LocalDateTime lastSent = (LocalDateTime) session.getAttribute("lastResendTime");

    if (resendCount == null) resendCount = 0;

    if (resendCount >= 3 && lastSent != null) {
        long secondsSince = ChronoUnit.SECONDS.between(lastSent, LocalDateTime.now());
        long cooldownSeconds = 15 * 60 - secondsSince;

        if (cooldownSeconds > 0) {
            model.addAttribute("otpError", "Too many attempts. Please wait 15 minutes before trying again.");
            model.addAttribute("otpCooldown", cooldownSeconds);
            return "otp-verify";
        }
    }

    String newOtp = String.format("%06d", new Random().nextInt(999999));
    session.setAttribute("otp", newOtp);
    session.setAttribute("otpTime", System.currentTimeMillis());
    session.setAttribute("expectedOtp", newOtp);
    session.setAttribute("otpTimestamp", LocalDateTime.now());
    session.setAttribute("resendCount", resendCount + 1);
    session.setAttribute("lastResendTime", LocalDateTime.now());

    mailService.sendOtpEmail(pendingEmail, newOtp);

    model.addAttribute("otpMessage", "A new OTP has been sent.");
    return "otp-verify";
}

@PostMapping("/forgot-password")
@ResponseBody
@Transactional
public org.springframework.http.ResponseEntity<String> forgotPassword(@RequestParam String email) {
    Optional<User> optionalUser = userRepository.findByEmail(email);

    // Always return generic response to prevent account enumeration.
    if (optionalUser.isEmpty()) {
        return org.springframework.http.ResponseEntity.ok("OK");
    }

    User user = optionalUser.get();
    String tempPass = UUID.randomUUID().toString().substring(0, 8) + "#";

    user.setPassword(BCrypt.hashpw(tempPass, BCrypt.gensalt()));
    user.setTempPasswordActive(true);
    userRepository.save(user);

    mailService.sendTempPasswordEmail(user.getEmail(), tempPass);
    return org.springframework.http.ResponseEntity.ok("OK");
}


    // ===================== PAGE ROUTES =====================
@GetMapping("/home.html")
public String showHomePage(@RequestParam(value = "guest", required = false) String guest,
                           HttpSession session,
                           Model model) {
    String email = (String) session.getAttribute("email");
    boolean guestBootstrapRequested = "1".equals(guest) || "true".equalsIgnoreCase(guest);

    if (email == null && guestBootstrapRequested) {
        guestSessionService.ensureGuestSession(session);
        email = (String) session.getAttribute("email");
    }

    if (email == null) {
        System.out.println("🚫 No email in session — redirecting to login");
        return "redirect:/login.html";
    }

    System.out.println("🏠 Home loaded. Email: " + email);
    model.addAttribute("email", email);

    boolean onboardingRequired = false;
    Optional<User> userOpt = userRepository.findByEmail(email);
    if (userOpt.isPresent()) {
        User user = userOpt.get();
        onboardingRequired = user.needsFirstLoginOnboarding();
        session.setAttribute("onboardingRequired", onboardingRequired);
        session.setAttribute("dataAnalysisConsent", user.getDataAnalysisConsent());
    }
    model.addAttribute("onboardingRequired", onboardingRequired);

    if (session.getAttribute("justLoggedIn") != null) {
        model.addAttribute("justLoggedIn", true);
        session.removeAttribute("justLoggedIn");
    }

    boolean forceDefaultTheme = Boolean.TRUE.equals(session.getAttribute("forceDefaultTheme"));
    model.addAttribute("forceDefaultTheme", forceDefaultTheme);
    if (forceDefaultTheme) {
        session.removeAttribute("forceDefaultTheme");
    }

    return "home.html";
}

    @GetMapping("/mood.html")
    public String moodPage(HttpSession session, Model model) {
        model.addAttribute("email", session.getAttribute("email"));
        return "mood";
    }

    @GetMapping("/journal.html")
    public String journalPage(HttpSession session, Model model) {
        model.addAttribute("email", session.getAttribute("email"));
        getCurrentUser(session).ifPresent(user -> {
            if (isJournalPinSet(user)) {
                session.removeAttribute(JOURNAL_PIN_VERIFIED_SESSION_KEY);
    session.removeAttribute(JOURNAL_PIN_VERIFIED_HASH_SESSION_KEY);
            }
        });
        return "journal.html";
    }

    @GetMapping("/affirmation.html")
    public String showAffirmationPage(HttpSession session, Model model) {
        model.addAttribute("email", session.getAttribute("email"));
        return "affirmation.html";
    }

   @GetMapping("/stats.html")
public String showStatsPage(HttpSession session, Model model) {
    String email = (String) session.getAttribute("email");
    model.addAttribute("email", email);

    if (email != null) {
        List<MoodLog> logs = moodLogRepository.findByUserEmail(email)
            .stream()
            .filter(log -> log.getTimestamp().isAfter(LocalDateTime.now().minusDays(7)))
            .collect(Collectors.toList());

        model.addAttribute("feelingsList", logs);
    }

    return "stats.html";
}

    @GetMapping("/music.html")
    public String showMusicPage(HttpSession session, Model model) {
        model.addAttribute("email", session.getAttribute("email"));
        model.addAttribute("initials", session.getAttribute("initials"));
        model.addAttribute("profileColor", session.getAttribute("profileColor"));
        model.addAttribute("profilePhoto", session.getAttribute("profilePhoto"));
        model.addAttribute("themeMode", session.getAttribute("themeMode"));
        model.addAttribute("themeHue", session.getAttribute("themeHue"));
        model.addAttribute("tracks", musicTrackRepository.findAll());
        return "music.html";
    }

    @GetMapping("/about.html")
    public String aboutPage(HttpSession session, Model model) {
        model.addAttribute("email", session.getAttribute("email"));
        return "about.html";
    }

    @GetMapping("/candle.html")
    public String showCandlePage(HttpSession session, Model model) {
        model.addAttribute("email", session.getAttribute("email"));
        return "candle.html";
    }

    @GetMapping("/profile.html")
    public String showProfilePage(HttpSession session, Model model) {
        String email = (String) session.getAttribute("email");
        if (email == null) return "redirect:/login.html";

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            LocalDate lastEdit = user.getLastNameChangeDate();
            LocalDate today = LocalDate.now();
            long daysSince = (lastEdit != null) ? ChronoUnit.DAYS.between(lastEdit, today) : 15;

            boolean canEdit = daysSince >= 14;
            String nameEditMessage = canEdit
                    ? "You can now change your name. (next allowed after 14 days)"
                    : "You can change your name in " + (14 - daysSince) + " days.";

            model.addAttribute("nameEditMessage", nameEditMessage);
            model.addAttribute("canEditName", canEdit);
            model.addAttribute("user", user);
            model.addAttribute("initials", session.getAttribute("initials"));
            model.addAttribute("profileColor", session.getAttribute("profileColor"));
            model.addAttribute("profilePhoto", session.getAttribute("profilePhoto"));

            List<Map<String, String>> countryCodes = new ArrayList<>();
            countryCodes.add(Map.of("value", "+91", "label", "+91 (India)"));
            countryCodes.add(Map.of("value", "+1", "label", "+1 (USA)"));
            countryCodes.add(Map.of("value", "+44", "label", "+44 (UK)"));
            model.addAttribute("countryCodes", countryCodes);
            model.addAttribute("countryCode", session.getAttribute("countryCode"));

            return "profile.html";
        }

        return "redirect:/login.html";
         }

@Transactional
   @PostMapping("/delete-user-data")
@ResponseBody
public ResponseEntity<String> deleteUserData(HttpSession session) {
    String email = (String) session.getAttribute("email");
    if (email == null) return ResponseEntity.status(401).body("No active session.");

    Optional<User> optionalUser = userRepository.findByEmail(email);
    if (optionalUser.isEmpty()) return ResponseEntity.status(404).body("User not found.");

    User user = optionalUser.get();

    journalEntryRepository.deleteByUser(user);
    moodLogRepository.deleteByUser(user);
    quickCheckinEntryRepository.deleteByUser(user);
    savedAffirmationRepository.deleteByUser(user);
    futureNoteRepository.deleteByUser(user);
    clearUserForecastAndPin(user, session);
    userRepository.save(user);

    return ResponseEntity.ok("App data deleted successfully.");
}

@Transactional
@PostMapping("/delete-account")
@ResponseBody
@SuppressWarnings("CallToPrintStackTrace")
public ResponseEntity<String> deleteAccount(HttpSession session) {
    String email = (String) session.getAttribute("email");
    if (email == null) return ResponseEntity.status(401).body("No active session.");

    Optional<User> optionalUser = userRepository.findByEmail(email);
    if (optionalUser.isEmpty()) return ResponseEntity.status(404).body("User not found.");

    User user = optionalUser.get();

    // Delete all user-linked app data
    journalEntryRepository.deleteByUser(user);
    moodLogRepository.deleteByUser(user);
    quickCheckinEntryRepository.deleteByUser(user);
    savedAffirmationRepository.deleteByUser(user);
    futureNoteRepository.deleteByUser(user);

    // Delete profile photo file if present
    if (user.getProfilePhoto() != null && !user.getProfilePhoto().isBlank()) {
        try {
            String fileName = user.getProfilePhoto()
                .substring(user.getProfilePhoto().lastIndexOf('/') + 1);
            Path photoPath = Paths.get("uploads", "profile-pics", fileName);
            Files.deleteIfExists(photoPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Delete user and logout
    userRepository.delete(user);
    session.invalidate();

    return ResponseEntity.ok("Account and all data deleted successfully.");
}

@PostMapping("/change-password")
@ResponseBody
public String changePassword(@RequestBody Map<String, String> payload, HttpSession session) {
    String email = (String) session.getAttribute("email");
    if (email == null) return "No active session";

    String oldPassword = payload.get("oldPassword");
    String newPassword = payload.get("newPassword");

    Optional<User> userOpt = userRepository.findByEmail(email);
    if (userOpt.isEmpty()) return "User not found";

    User user = userOpt.get();

    if (!BCrypt.checkpw(oldPassword, user.getPassword())) {
        return "Incorrect current password";
    }

    user.setPassword(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
    userRepository.save(user);
    return "Password updated successfully!";
}

@GetMapping("/export-data")
public void exportUserData(HttpServletResponse response, HttpSession session) throws IOException {
    String email = (String) session.getAttribute("email");
    if (email == null) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return;
    }

    Optional<User> userOpt = userRepository.findByEmail(email);
    if (userOpt.isEmpty()) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return;
    }

    User user = userOpt.get();

    List<MoodLog> moodLogs = new ArrayList<>(moodLogRepository.findByUserEmail(email));
    moodLogs.sort(Comparator.comparing(MoodLog::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())));

    List<JournalEntry> journalEntries = new ArrayList<>(journalEntryRepository.findByUserEmail(email));
    journalEntries.sort(Comparator.comparing(JournalEntry::getDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(JournalEntry::getTime, Comparator.nullsLast(Comparator.naturalOrder())));

    List<SavedAffirmation> affirmations = new ArrayList<>(savedAffirmationRepository.findByUserEmail(email));
    List<FutureNote> futureNotes = new ArrayList<>(futureNoteRepository.findByUserEmail(email));
    futureNotes.sort(Comparator.comparing(FutureNote::getId, Comparator.nullsLast(Comparator.naturalOrder())));

    List<QuickCheckinEntry> quickCheckins = new ArrayList<>(quickCheckinEntryRepository.findByUserEmail(email));
    quickCheckins.sort(Comparator.comparing(QuickCheckinEntry::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));

    byte[] pdfBytes;
    try (PDDocument document = new PDDocument()) {
        try (PdfTextWriter writer = new PdfTextWriter(document)) {
            writer.addTitle("Zyneé Wellness Journey Report");
            writer.addLine("Generated On: " + LocalDateTime.now());
            writer.addLine("User: " + (user.getName() == null ? user.getEmail() : user.getName()) + " (" + user.getEmail() + ")");
            writer.addLine("Coverage: Account history available in database until now");
            writer.blankLine();

            writeLifetimeSummary(writer, moodLogs, journalEntries, quickCheckins, futureNotes, affirmations);
            writeMoodAnalysisSection(writer, moodLogs);
            writeQuickCheckinAnalysisSection(writer, quickCheckins);
            writeJournalAnalysisSection(writer, journalEntries);
            writeFutureNotesSection(writer, futureNotes);
            writeAffirmationSection(writer, affirmations);
            writeRawLogsSection(writer, moodLogs, journalEntries, quickCheckins);
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.save(output);
            pdfBytes = output.toByteArray();
        }

        if (!isValidPdfPayload(pdfBytes)) {
            throw new IOException("Generated PDF failed validation.");
        }
    } catch (Exception ex) {
        //ex.printStackTrace();
        if (!response.isCommitted()) {
            response.reset();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("Failed to generate PDF report.");
        }
        return;
    }

    response.reset();
    String exportFileName = "zynee_wellness_report_" +
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".pdf";
    response.setContentType("application/pdf");
    response.setHeader("Content-Disposition", "attachment; filename=\"" + exportFileName + "\"");
    response.setHeader("X-Export-Version", "zynee-export-20260416");
    response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Content-Transfer-Encoding", "binary");
    response.setDateHeader("Expires", 0);
    response.setContentLengthLong(pdfBytes.length);
    try (ServletOutputStream outputStream = response.getOutputStream()) {
        outputStream.write(pdfBytes);
        outputStream.flush();
    }
    response.flushBuffer();

    System.out.println("✅ Exported PDF " + exportFileName + " | bytes=" + pdfBytes.length + " | version=zynee-export-20260416");
}

private boolean isValidPdfPayload(byte[] bytes) {
    if (bytes == null || bytes.length < 32) {
        return false;
    }

    String head = new String(bytes, 0, Math.min(8, bytes.length), StandardCharsets.ISO_8859_1);
    if (!head.startsWith("%PDF-")) {
        return false;
    }

    String tail = new String(bytes, Math.max(0, bytes.length - 2048), Math.min(2048, bytes.length), StandardCharsets.ISO_8859_1);
    if (!tail.contains("%%EOF") || !tail.contains("xref") || !tail.contains("trailer")) {
        return false;
    }

    try (PDDocument verify = PDDocument.load(bytes)) {
        return verify.getNumberOfPages() >= 1;
    } catch (Exception ex) {
        return false;
    }
}

private void writeLifetimeSummary(
        PdfTextWriter writer,
        List<MoodLog> moodLogs,
        List<JournalEntry> journalEntries,
        List<QuickCheckinEntry> quickCheckins,
        List<FutureNote> futureNotes,
        List<SavedAffirmation> affirmations) throws IOException {
    writer.addSection("Journey Overview");

    List<LocalDateTime> timeline = new ArrayList<>();
    moodLogs.stream().map(MoodLog::getTimestamp).filter(t -> t != null).forEach(timeline::add);
    journalEntries.stream()
            .filter(entry -> entry.getDate() != null)
            .map(entry -> {
                LocalTime time = entry.getTime() == null ? LocalTime.MIDNIGHT : entry.getTime();
                return entry.getDate().atTime(time);
            })
            .forEach(timeline::add);
    quickCheckins.stream().map(QuickCheckinEntry::getCreatedAt).filter(t -> t != null).forEach(timeline::add);
    futureNotes.stream().map(FutureNote::getCreatedAt).filter(t -> t != null).forEach(timeline::add);
    affirmations.stream().map(SavedAffirmation::getSavedAt).filter(t -> t != null).forEach(timeline::add);

    timeline.sort(Comparator.naturalOrder());
    String coverage = timeline.isEmpty()
            ? "No activity logs available yet."
            : timeline.get(0).toLocalDate() + " to " + LocalDate.now();

    writer.addBullet("Coverage Window: " + coverage);
    writer.addBullet("Mood logs: " + moodLogs.size());
    writer.addBullet("Journal entries: " + journalEntries.size());
    writer.addBullet("Quick check-ins: " + quickCheckins.size());
    writer.addBullet("Future notes: " + futureNotes.size());
    writer.addBullet("Saved affirmations: " + affirmations.size());
}

private void writeMoodAnalysisSection(PdfTextWriter writer, List<MoodLog> moodLogs) throws IOException {
    writer.addSection("Stats, Insights & Mood Analysis");
    if (moodLogs.isEmpty()) {
        writer.addLine("No mood logs available, so mood-based analysis is currently unavailable.");
        return;
    }

    double avgMood = moodLogs.stream()
            .mapToInt(log -> normalizeMoodLevel(log.getMoodLevel()))
            .average()
            .orElse(0.0);
    long lowDays = moodLogs.stream().filter(log -> normalizeMoodLevel(log.getMoodLevel()) <= 2).count();
    long goodDays = moodLogs.stream().filter(log -> normalizeMoodLevel(log.getMoodLevel()) >= 4).count();

    Map<String, Integer> feelingCounts = new HashMap<>();
    Map<String, Integer> triggerCounts = new HashMap<>();
    for (MoodLog log : moodLogs) {
        normalizeTextList(log.getFeelings(), 80, 80).forEach(feeling -> incrementCount(feelingCounts, feeling));
        normalizeTextList(log.getTriggerTags(), 80, 120).forEach(trigger -> incrementCount(triggerCounts, trigger));
    }

    writer.addBullet("Average mood score: " + String.format("%.2f/5", avgMood));
    writer.addBullet("Lower-mood logs (<=2): " + lowDays);
    writer.addBullet("Positive-mood logs (>=4): " + goodDays);
    writer.addBullet("Top feelings: " + topItemsText(feelingCounts, 8));
    writer.addBullet("Top mood triggers: " + topItemsText(triggerCounts, 8));
}

private void writeQuickCheckinAnalysisSection(PdfTextWriter writer, List<QuickCheckinEntry> quickCheckins) throws IOException {
    writer.addSection("Quick Check-In Reports & Weekly Pattern Signals");
    if (quickCheckins.isEmpty()) {
        writer.addLine("No quick check-ins available, so report indicators are unavailable.");
        return;
    }

    double avgScore = quickCheckins.stream().mapToDouble(QuickCheckinEntry::getAverageScore).average().orElse(0.0);
    double avgConfidence = quickCheckins.stream().mapToInt(QuickCheckinEntry::getResponseConfidence).average().orElse(0.0);
    long highStrain = quickCheckins.stream()
            .filter(entry -> entry.getAverageScore() <= 2.6 || entry.getLowSignalCount() >= 3)
            .count();

    Map<String, Integer> checkinTriggerCounts = new HashMap<>();
    for (QuickCheckinEntry entry : quickCheckins) {
        List<String> triggerTags = extractQuickCheckinTags(entry);
        triggerTags.forEach(tag -> incrementCount(checkinTriggerCounts, tag));
    }

    writer.addBullet("Average check-in score: " + String.format("%.2f/5", avgScore));
    writer.addBullet("Average completion confidence: " + Math.round(avgConfidence) + "%");
    writer.addBullet("High-strain check-ins: " + highStrain);
    writer.addBullet("Top quick-checkin triggers: " + topItemsText(checkinTriggerCounts, 8));
}

private void writeJournalAnalysisSection(PdfTextWriter writer, List<JournalEntry> journalEntries) throws IOException {
    writer.addSection("Journal Activity Analysis");
    if (journalEntries.isEmpty()) {
        writer.addLine("No journal entries available.");
        return;
    }

    LocalDate firstDate = journalEntries.stream()
            .map(JournalEntry::getDate)
            .filter(date -> date != null)
            .min(LocalDate::compareTo)
            .orElse(LocalDate.now());

    long activeDays = journalEntries.stream()
            .map(JournalEntry::getDate)
            .filter(date -> date != null)
            .distinct()
            .count();

    long totalWords = journalEntries.stream()
            .map(JournalEntry::getContent)
            .filter(content -> content != null && !content.isBlank())
            .mapToLong(content -> content.trim().split("\\s+").length)
            .sum();

    long lifetimeDays = Math.max(1, ChronoUnit.DAYS.between(firstDate, LocalDate.now()) + 1);
    double entriesPerWeek = (journalEntries.size() * 7.0) / lifetimeDays;

    writer.addBullet("Journal active days: " + activeDays);
    writer.addBullet("Total journal words written: " + totalWords);
    writer.addBullet("Average entries/week (lifetime): " + String.format("%.2f", entriesPerWeek));
}

private void writeFutureNotesSection(PdfTextWriter writer, List<FutureNote> futureNotes) throws IOException {
    writer.addSection("Future Notes Timeline");
    if (futureNotes.isEmpty()) {
        writer.addLine("No future notes saved.");
        return;
    }

    long opened = futureNotes.stream().filter(note -> note.getOpenedAt() != null).count();
    writer.addBullet("Future notes created: " + futureNotes.size());
    writer.addBullet("Future notes opened: " + opened);
}

private void writeAffirmationSection(PdfTextWriter writer, List<SavedAffirmation> affirmations) throws IOException {
    writer.addSection("Affirmation Usage");
    if (affirmations.isEmpty()) {
        writer.addLine("No affirmations saved yet.");
        return;
    }

    Map<String, Integer> affirmationCounts = new HashMap<>();
    for (SavedAffirmation entry : affirmations) {
        String message = entry.getAffirmation() == null ? "" : entry.getAffirmation().getMessage();
        String normalized = message == null ? "" : message.trim();
        if (!normalized.isBlank()) {
            incrementCount(affirmationCounts, normalized);
        }
    }

    writer.addBullet("Affirmations saved: " + affirmations.size());
    writer.addBullet("Most used affirmations: " + topItemsText(affirmationCounts, 5));
}

private void writeRawLogsSection(
        PdfTextWriter writer,
        List<MoodLog> moodLogs,
        List<JournalEntry> journalEntries,
        List<QuickCheckinEntry> quickCheckins) throws IOException {
    writer.addSection("Detailed Log History");
    writer.addLine("This section includes currently available records from account start to present.");

    writer.addLine("");
    writer.addLine("Mood Logs:");
    if (moodLogs.isEmpty()) {
        writer.addLine("  - No mood logs found.");
    } else {
        for (MoodLog log : moodLogs) {
            String time = log.getTimestamp() == null ? "-" : log.getTimestamp().toString();
            String feelings = formatList(normalizeTextList(log.getFeelings(), 50, 80));
            String triggers = formatList(normalizeTextList(log.getTriggerTags(), 20, 120));
            writer.addLine("  - " + time
                    + " | Mood " + normalizeMoodLevel(log.getMoodLevel()) + "/5"
                    + " | Feelings: " + feelings
                    + " | Triggers: " + triggers);
        }
    }

    writer.addLine("");
    writer.addLine("Journal Entries:");
    if (journalEntries.isEmpty()) {
        writer.addLine("  - No journal entries found.");
    } else {
        for (JournalEntry entry : journalEntries) {
            String stamp = String.valueOf(entry.getDate()) + " " + String.valueOf(entry.getTime());
            String preview = entry.getContent() == null ? "" : entry.getContent().trim();
            if (preview.length() > 200) {
                preview = preview.substring(0, 200) + "...";
            }
            writer.addLine("  - " + stamp + " | " + preview);
        }
    }

    writer.addLine("");
    writer.addLine("Quick Check-Ins:");
    if (quickCheckins.isEmpty()) {
        writer.addLine("  - No quick check-ins found.");
    } else {
        for (QuickCheckinEntry entry : quickCheckins) {
            String stamp = entry.getCreatedAt() == null ? "-" : entry.getCreatedAt().toString();
            String triggers = formatList(extractQuickCheckinTags(entry));
            writer.addLine("  - " + stamp
                    + " | Mood Label: " + String.valueOf(entry.getMoodLabel())
                    + " | Avg Score: " + String.format("%.2f", entry.getAverageScore())
                    + " | Triggers: " + triggers);
        }
    }
}

@SuppressWarnings("unchecked")
private List<String> extractQuickCheckinTags(QuickCheckinEntry entry) {
    List<String> stored = normalizeTextList(entry.getMoodTriggerTags(), 20, 120);
    if (!stored.isEmpty()) {
        return stored;
    }

    try {
        Map<String, Object> answers = objectMapper.readValue(entry.getAnswersJson(), Map.class);
        Object raw = answers.get("mood_trigger_tags");
        if (!(raw instanceof Map<?, ?> rawMap)) {
            return List.of();
        }
        Object tagsRaw = rawMap.get("tags");
        if (!(tagsRaw instanceof List<?> tagsList)) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (Object item : tagsList) {
            String text = item == null ? "" : String.valueOf(item).trim();
            if (!text.isBlank() && !tags.contains(text)) {
                tags.add(text);
            }
        }
        return tags;
    } catch (JsonProcessingException ignored) {
        return List.of();
    }
}

private void incrementCount(Map<String, Integer> counter, String rawKey) {
    if (rawKey == null) {
        return;
    }
    String key = rawKey.trim();
    if (key.isBlank()) {
        return;
    }
    counter.put(key, counter.getOrDefault(key, 0) + 1);
}

private String topItemsText(Map<String, Integer> counts, int limit) {
    if (counts == null || counts.isEmpty()) {
        return "-";
    }
    return counts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                    .thenComparing(Map.Entry::getKey))
            .limit(Math.max(1, limit))
            .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
            .collect(Collectors.joining(", "));
}

private String formatList(List<String> values) {
    List<String> safe = values == null ? List.of() : values.stream()
            .filter(value -> value != null && !value.isBlank())
            .toList();
    if (safe.isEmpty()) {
        return "-";
    }
    return String.join(", ", safe);
}

private static final class PdfTextWriter implements AutoCloseable {
    private static final float FONT_SIZE = 11f;
    private static final float LINE_GAP = 15f;
    private static final float MARGIN = 48f;

    private final PDDocument document;
    private PDPage page;
    private PDPageContentStream stream;
    private float y;

    private PdfTextWriter(PDDocument document) throws IOException {
        this.document = document;
        startNewPage();
    }

    private void addTitle(String text) throws IOException {
        writeWrapped(text, PDType1Font.HELVETICA_BOLD, 17f);
        blankLine();
    }

    private void addSection(String text) throws IOException {
        blankLine();
        writeWrapped(text, PDType1Font.HELVETICA_BOLD, 13f);
    }

    private void addLine(String text) throws IOException {
        writeWrapped(text, PDType1Font.HELVETICA, FONT_SIZE);
    }

    private void addBullet(String text) throws IOException {
        writeWrapped("- " + text, PDType1Font.HELVETICA, FONT_SIZE);
    }

    private void blankLine() throws IOException {
        ensureSpace(LINE_GAP);
        y -= LINE_GAP;
    }

    private void writeWrapped(String text, PDFont font, float fontSize) throws IOException {
        String safeText = text == null ? "" : text;
        if (safeText.isEmpty()) {
            blankLine();
            return;
        }

        String[] rawLines = safeText.split("\\r?\\n");
        for (String rawLine : rawLines) {
            writeLineInternal(rawLine, font, fontSize);
        }
    }

    private void writeLineInternal(String rawLine, PDFont font, float fontSize) throws IOException {
        String line = sanitizeForFont(rawLine == null ? "" : rawLine, font);
        if (line.isBlank()) {
            ensureSpace(LINE_GAP);
            y -= LINE_GAP;
            return;
        }

        float contentWidth = page.getMediaBox().getWidth() - (2 * MARGIN);
        String[] words = line.split("\\s+");
        StringBuilder builder = new StringBuilder();

        for (String word : words) {
            String candidate = builder.length() == 0 ? word : builder + " " + word;
            float candidateWidth = font.getStringWidth(candidate) / 1000f * fontSize;
            if (candidateWidth > contentWidth && builder.length() > 0) {
                writeSingleLine(builder.toString(), font, fontSize);
                builder.setLength(0);
                builder.append(word);
            } else {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(word);
            }
        }

        if (builder.length() > 0) {
            writeSingleLine(builder.toString(), font, fontSize);
        }
    }

    private String sanitizeForFont(String text, PDFont font) throws IOException {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            String candidate = String.valueOf(ch);
            try {
                font.getStringWidth(candidate);
                builder.append(ch);
            } catch (IllegalArgumentException ignored) {
                builder.append(Character.isWhitespace(ch) ? ' ' : '?');
            }
        }
        return builder.toString();
    }

    private void writeSingleLine(String text, PDFont font, float fontSize) throws IOException {
        ensureSpace(LINE_GAP);
        stream.beginText();
        stream.setFont(font, fontSize);
        stream.newLineAtOffset(MARGIN, y);
        stream.showText(text);
        stream.endText();
        y -= LINE_GAP;
    }

    private void ensureSpace(float needed) throws IOException {
        if (y - needed > MARGIN) {
            return;
        }
        startNewPage();
    }

    private void startNewPage() throws IOException {
        if (stream != null) {
            stream.close();
        }
        page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        stream = new PDPageContentStream(document, page);
        y = page.getMediaBox().getHeight() - MARGIN;
    }

    @Override
    public void close() throws IOException {
        if (stream != null) {
            stream.close();
        }
    }
}

// ✅ Helper: Setup session after login
private void setupSessionAfterLogin(User user, HttpSession session) {
    // Generate initials
    String name = safeTrim(user.getName());
    if (name.isEmpty()) {
        name = "User";
    }
    String initials = name.substring(0, 1).toUpperCase();

    // Profile color
    String[] colors = {"#e57373", "#64b5f6", "#81c784", "#ffd54f", "#4db6ac", "#ba68c8"};
    String profileColor = colors[new Random().nextInt(colors.length)];

    // Extract phone & country code
    List<String> knownCountryCodes = List.of("+91", "+1", "+44", "+971", "+61", "+81", "+86", "+880", "+49");
    String fullPhone = safeTrim(user.getPhone());
    String matchedCode = knownCountryCodes.stream()
        .filter(fullPhone::startsWith)
        .findFirst()
        .orElse("+91");
    String phone = fullPhone.startsWith(matchedCode)
        ? fullPhone.substring(matchedCode.length())
        : fullPhone;

    // Set session attributes
    session.setAttribute("email", user.getEmail());
    session.setAttribute("name", name);
    session.setAttribute("gender", user.getGender());
    session.setAttribute("dob", user.getDob());
    session.setAttribute("themeMode", user.getThemeMode());
    session.setAttribute("themeHue", user.getThemeHue());
    session.setAttribute("initials", initials);
    session.setAttribute("profileColor", profileColor);
    session.setAttribute("profilePhoto", user.getProfilePhoto());
    session.setAttribute("phone", phone);
    session.setAttribute("countryCode", matchedCode);
    session.setAttribute("justLoggedIn", true);
    session.setAttribute("user", user);
    session.setAttribute("userId", user.getId());
    session.setAttribute("assistantSessionKey", UUID.randomUUID().toString());
    session.setAttribute("isGuest", false);
    session.removeAttribute(JOURNAL_PIN_VERIFIED_SESSION_KEY);
    session.removeAttribute(JOURNAL_PIN_VERIFIED_HASH_SESSION_KEY);
}
@Controller
public class GameController {
    
    @GetMapping("/games/rps")
    public String showRpsGame() {
        return "redirect:/home.html";
    }
@Controller
public class PageController {

    @GetMapping({"/ai", "/ai-assistant.html"})
    public String aiAssistant() {
        return "ai-assistant"; // no .html here
    }
}}
}
