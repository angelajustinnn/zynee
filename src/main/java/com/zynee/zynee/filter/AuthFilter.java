package com.zynee.zynee.filter;

import java.io.IOException;

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
public class AuthFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        HttpSession session = req.getSession(false); // ⛔ don't create a new session

        String path = req.getRequestURI();
        String guestParam = req.getParameter("guest");
        boolean isGuestBootstrapHome = (path.endsWith("/home.html") || path.endsWith("/home"))
                && ("1".equals(guestParam) || "true".equalsIgnoreCase(guestParam));
        boolean isGuest = session != null && Boolean.TRUE.equals(session.getAttribute("isGuest"));
        boolean isLoggedIn = session != null && session.getAttribute("email") != null;
        boolean isAuthenticated = isLoggedIn || isGuest;

        // ✅ Allowlist: public/static pages (can be expanded)
        boolean isPublicPath = path.contains("/login") ||
                               path.contains("/signup") ||
                               path.contains("/guest-access") ||
                               path.contains("/verify-otp") ||
                               path.contains("/otp-verify") ||
                               path.contains("/terms") ||
                               path.endsWith("/forgot-password") ||
                               path.endsWith(".css") || path.endsWith(".js") ||
                               path.endsWith(".png") || path.endsWith(".jpg") || 
                               path.endsWith(".jpeg") || path.endsWith(".svg") ||
                               path.endsWith(".ico") || // favicon
                               path.contains("/static/") ||
                               path.startsWith("/api/public") ||
                               isGuestBootstrapHome; // guest bootstrap path

        // ✅ DEBUG
        System.out.println("🔍 Filter Check → Path: " + path);
        System.out.println("   ➤ Logged In? " + isLoggedIn);
        System.out.println("   ➤ Guest? " + isGuest);

        if (isLoggedIn && !isGuest && path.contains("/login")) {
    // ⛔ Prevent logged-in users from accessing login page again
    res.sendRedirect("/home.html");
} else if (isAuthenticated || isPublicPath) {
    chain.doFilter(request, response); // ✅ Allow request
} else {
    System.out.println("⛔ Redirecting to /login.html due to no session.");
    res.sendRedirect("/login.html");   // 🔁 Force login
}
            }
}
