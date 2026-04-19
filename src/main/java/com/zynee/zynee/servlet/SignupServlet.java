package com.zynee.zynee.servlet;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;

import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.support.SpringBeanAutowiringSupport;

import com.zynee.zynee.model.User;
import com.zynee.zynee.repository.UserRepository;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/signup")
@Component
public class SignupServlet extends HttpServlet {

    @Autowired
    private UserRepository userRepository;

    @Override
    public void init() {
        SpringBeanAutowiringSupport.processInjectionBasedOnCurrentContext(this);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        String name = request.getParameter("name").trim();
        String email = request.getParameter("email").trim();
       String countryCode = request.getParameter("countryCode").trim();
String phone = request.getParameter("phone").trim();
String fullPhone = countryCode + phone;

// Extract plain phone number (e.g., from +91xxxxxxxxxx → xxxxxxxxxx)
String plainPhone = fullPhone.startsWith(countryCode) ? fullPhone.substring(countryCode.length()) : fullPhone;

// ✅ Store phone number and country code in session
request.getSession().setAttribute("phone", plainPhone);
request.getSession().setAttribute("countryCode", countryCode);

        String gender = request.getParameter("gender").trim();
        String dob = request.getParameter("dob").trim();
        String password = request.getParameter("password").trim();
        String confirmPassword = request.getParameter("confirmPassword").trim();

        // ✅ Password and input validation
        String error = null;

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            error = "Missing required fields.";
        } else if (!password.equals(confirmPassword)) {
            error = "Passwords do not match.";
        } else if (password.length() < 8) {
            error = "Password must be at least 8 characters long.";
        } else if (!password.matches(".*[A-Z].*")) {
            error = "Password must contain at least one uppercase letter.";
        } else if (!password.matches(".*[a-z].*")) {
            error = "Password must contain at least one lowercase letter.";
        } else if (!password.matches(".*\\d.*")) {
            error = "Password must contain at least one number.";
        } else if (!password.matches(".*[^a-zA-Z0-9].*")) {
            error = "Password must contain at least one special character.";
        }

        if (error != null) {
            response.sendRedirect("signup.html?error=" + URLEncoder.encode(error, StandardCharsets.UTF_8));
            return;
        }

        // ✅ Check if user already exists
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            response.sendRedirect("signup.html?error=" + URLEncoder.encode("Email already registered", StandardCharsets.UTF_8));
            return;
        }

        // ✅ Save user
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPhone(fullPhone); // ✅ saves +91xxxxxxxxxx
        user.setCountryCode(countryCode); // ✅ Save country code separately
        user.setGender(gender);
        user.setDob(LocalDate.parse(dob));
        user.setPassword(hashedPassword);
        user.setCreatedAt(LocalDate.now());
        user.setOnboardingTourCompleted(false);

        userRepository.save(user);

        // ✅ Redirect to login with success flag
        response.sendRedirect("login.html?signup=success");
    }
}
