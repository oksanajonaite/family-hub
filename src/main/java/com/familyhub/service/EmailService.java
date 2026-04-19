package com.familyhub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    // Sender address — falls back to a dev placeholder when spring.mail.username
    // is not set (e.g. when using Mailpit locally with no credentials configured).
    @Value("${spring.mail.username:noreply@familyhub.dev}")
    private String fromAddress;

    // Sends a password reset link to the given email address.
    // The token is embedded in the URL — the recipient clicks it to open the reset form.
    public void sendPasswordReset(String toEmail, String token) {
        String resetUrl = "http://localhost:8080/reset-password?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Family Hub — Password Reset");
        message.setText(
                "Hello,\n\n" +
                "You requested a password reset for your Family Hub account.\n\n" +
                "Click the link below to reset your password (valid for 1 hour):\n" +
                resetUrl + "\n\n" +
                "If you did not request this, you can safely ignore this email.\n\n" +
                "— Family Hub"
        );

        mailSender.send(message);
        log.info("Password reset email sent to: {}", toEmail);
    }
}
