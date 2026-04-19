package com.zynee.zynee;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebFilter("/*")
public class SessionFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        HttpSession session = req.getSession(false);

        String requestUri = req.getRequestURI();
        String contextPath = req.getContextPath();
        String path = requestUri.startsWith(contextPath)
                ? requestUri.substring(contextPath.length())
                : requestUri;
        if (path.isEmpty()) {
            path = "/";
        }

        boolean isGuest = (session != null && Boolean.TRUE.equals(session.getAttribute("isGuest")));
        boolean isLoggedIn = (session != null && session.getAttribute("email") != null);
        boolean isAuthenticated = isLoggedIn || isGuest;
        boolean isOtpInProgress = (session != null && session.getAttribute("otp") != null);
        if (isAuthenticated && session != null && session.getAttribute("assistantSessionKey") == null) {
            session.setAttribute("assistantSessionKey", UUID.randomUUID().toString());
        }

        boolean isLoginPage = path.equals("/login") || path.equals("/login.html");
        boolean isSignupPage = path.equals("/signup") || path.equals("/signup.html");
        boolean isGuestAccess = path.equals("/guest-access");
        String guestParam = req.getParameter("guest");
        boolean isGuestBootstrapHome = path.equals("/home.html")
                && ("1".equals(guestParam) || "true".equalsIgnoreCase(guestParam));
        boolean isOtpPage = path.equals("/otp-verify") || path.equals("/verify-otp") || path.equals("/resend-otp");
        boolean isTermsPage = path.equals("/terms");
        boolean isForgotPassword = path.equals("/forgot-password");
        boolean isLogout = path.equals("/logout");
        boolean isErrorPath = path.equals("/error");

        // If user explicitly visits login while an OTP flow is stale, reset OTP session and allow fresh login.
        if (!isLoggedIn && isLoginPage && isOtpInProgress && session != null) {
            session.removeAttribute("otp");
            session.removeAttribute("otpTime");
            session.removeAttribute("expectedOtp");
            session.removeAttribute("otpTimestamp");
            session.removeAttribute("lastResendTime");
            session.removeAttribute("resendCount");
            session.removeAttribute("pendingUserId");
            session.removeAttribute("pendingEmail");
            isOtpInProgress = false;
        }

        boolean isStatic = path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/")
                || path.startsWith("/music/")
                || path.startsWith("/profile-pics/")
                || path.startsWith("/fonts/")
                || path.endsWith(".ico")
                || path.endsWith(".mp3")
                || path.endsWith(".wav")
                || path.endsWith(".woff")
                || path.endsWith(".woff2")
                || path.endsWith(".ttf")
                || path.endsWith(".svg")
                || path.endsWith(".eot");

        // Prevent protected-page back/forward access after logout.
        if (!isStatic) {
            res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            res.setHeader("Pragma", "no-cache");
            res.setDateHeader("Expires", 0);
        }

        // If a logged-in user navigates back to login, force re-authentication.
        if (isLoggedIn && !isGuest && isLoginPage) {
            if (session != null) {
                session.invalidate();
            }
            res.sendRedirect(contextPath + "/login.html?reauth=true");
            return;
        }

        // Logged-in users should not visit signup or OTP pages.
        if (isLoggedIn && !isGuest && (isSignupPage || isOtpPage)) {
            res.sendRedirect(contextPath + "/home.html");
            return;
        }

        // Guests can view pages, but cannot perform write actions.
        if (isGuest && isMutatingMethod(req.getMethod()) && !isGuestAllowedMutation(path)) {
            if (expectsJsonResponse(req)) {
                res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                res.setContentType("application/json");
                res.getWriter().write("{\"guestBlocked\":true,\"message\":\"Guest mode is view-only.\"}");
            } else {
                res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                res.setContentType("text/plain;charset=UTF-8");
                res.getWriter().write("Guest mode is view-only.");
            }
            return;
        }

        // OTP pages are allowed only while OTP is in progress.
        if (isOtpPage) {
            if (isOtpInProgress) {
                chain.doFilter(request, response);
            } else {
                res.sendRedirect(contextPath + "/login.html");
            }
            return;
        }

        // If OTP is pending, block all other app pages until OTP is verified.
        if (isOtpInProgress && !isLogout && !isStatic) {
            res.sendRedirect(contextPath + "/otp-verify");
            return;
        }

        boolean isPublic = isLoginPage || isSignupPage || isGuestAccess || isGuestBootstrapHome
                || isTermsPage || isForgotPassword || isLogout || isErrorPath || isStatic;

        if (isAuthenticated || isPublic) {
            chain.doFilter(request, response);
        } else {
            res.sendRedirect(contextPath + "/login.html");
        }
    }

    private boolean isMutatingMethod(String method) {
        if (method == null) {
            return false;
        }
        return !("GET".equalsIgnoreCase(method)
                || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method));
    }

    private boolean isGuestAllowedMutation(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String normalized = path.toLowerCase();
        return normalized.equals("/logout")
                || normalized.equals("/guest-access")
                || normalized.equals("/login")
                || normalized.equals("/login.html")
                || normalized.equals("/forgot-password")
                || normalized.equals("/api/emotional-assistant/chat");
    }

    private boolean expectsJsonResponse(HttpServletRequest req) {
        String accept = req.getHeader("Accept");
        String requestedWith = req.getHeader("X-Requested-With");
        String contentType = req.getContentType();
        return (accept != null && accept.toLowerCase().contains("application/json"))
                || "XMLHttpRequest".equalsIgnoreCase(requestedWith)
                || (contentType != null && contentType.toLowerCase().contains("application/json"));
    }
}
