package com.zynee.zynee.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String configuredFromEmail;

    private String resolveFromEmail() {
        if (configuredFromEmail == null || configuredFromEmail.isBlank()) {
            return "zynee.help@gmail.com";
        }
        return configuredFromEmail;
    }

    public void sendOtpEmail(String toEmail, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(resolveFromEmail());
        message.setTo(toEmail);
        message.setSubject("Zyneé OTP Verification");
        message.setText("""
            Hello,

            Your One-Time Password (OTP) is: %s

            Please enter it to complete your login.

            - Team Zynee
            """.formatted(otp));

        mailSender.send(message);
    }

    public void sendTempPasswordEmail(String toEmail, String tempPassword) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(resolveFromEmail());
        message.setTo(toEmail);
        message.setSubject("Zyneé Temporary Password");
        message.setText("""
            Hello,

            Here is your one-time temporary password to log in to Zyneé:

            %s

            ⚠️ This password is valid only once. Please log in and change your password immediately after logging in.

            If you didn’t request this, you can safely ignore this message.

            - Team Zyneé
            """.formatted(tempPassword));

        mailSender.send(message);
    }
}
