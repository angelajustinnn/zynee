package com.zynee.zynee.service;

import java.time.LocalDate;
import java.util.UUID;

import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;

import com.zynee.zynee.model.User;
import com.zynee.zynee.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Service
public class GuestSessionService {

    private final UserRepository userRepository;

    public GuestSessionService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void startGuestSession(HttpServletRequest request) {
        HttpSession existingSession = request.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }
        HttpSession session = request.getSession(true);
        attachFreshGuest(session);
    }

    public boolean ensureGuestSession(HttpSession session) {
        if (session == null) {
            return false;
        }

        boolean alreadyGuest = Boolean.TRUE.equals(session.getAttribute("isGuest"));
        boolean hasEmail = session.getAttribute("email") != null;
        if (alreadyGuest && hasEmail) {
            return true;
        }

        attachFreshGuest(session);
        return session.getAttribute("email") != null;
    }

    private void attachFreshGuest(HttpSession session) {
        User guestUser = createGuestUser();
        try {
            guestUser = userRepository.save(guestUser);
        } catch (Exception ex) {
            // Keep guest entry resilient even if DB insert fails unexpectedly.
            System.err.println("⚠️ Guest user DB save failed: " + ex.getMessage());
        }

        String sessionKey = UUID.randomUUID().toString();
        session.setAttribute("email", guestUser.getEmail());
        session.setAttribute("name", guestUser.getName());
        session.setAttribute("gender", null);
        session.setAttribute("dob", null);
        session.setAttribute("themeMode", guestUser.getThemeMode());
        session.setAttribute("themeHue", guestUser.getThemeHue());
        session.setAttribute("initials", "G");
        session.setAttribute("profileColor", "#8b5da9");
        session.setAttribute("profilePhoto", null);
        session.setAttribute("phone", "");
        session.setAttribute("countryCode", "");
        session.setAttribute("justLoggedIn", true);
        session.setAttribute("user", guestUser);
        session.setAttribute("userId", guestUser.getId());
        session.setAttribute("assistantSessionKey", sessionKey);
        session.setAttribute("isGuest", true);
        session.setAttribute("onboardingRequired", true);
        session.setAttribute("forceDefaultTheme", true);
        session.setAttribute("dataAnalysisConsent", null);
        session.removeAttribute("otp");
        session.removeAttribute("otpTime");
        session.removeAttribute("expectedOtp");
        session.removeAttribute("otpTimestamp");
        session.removeAttribute("lastResendTime");
        session.removeAttribute("resendCount");
        session.removeAttribute("pendingUserId");
        session.removeAttribute("pendingEmail");
    }

    private User createGuestUser() {
        User user = new User();
        user.setName("Guest User");
        user.setEmail(buildGuestEmail());
        user.setPassword(BCrypt.hashpw(UUID.randomUUID().toString(), BCrypt.gensalt()));
        user.setCreatedAt(LocalDate.now());
        user.setOnboardingTourCompleted(false);
        user.setDataAnalysisConsent(null);
        user.setThemeMode("default");
        user.setThemeHue("270");
        user.setCountryCode("");
        user.setPhone("");
        return user;
    }

    private String buildGuestEmail() {
        return "guest+" + UUID.randomUUID().toString().replace("-", "") + "@zynee.local";
    }
}
