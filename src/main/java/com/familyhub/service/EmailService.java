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

    // Verified sender address used in the From: header.
    // Must match a sender validated in Brevo (Senders & IPs → Senders).
    // Configured separately from spring.mail.username because Brevo's SMTP login
    // (a887e0001@smtp-brevo.com) differs from the actual sender address.
    @Value("${app.mail.from}")
    private String fromAddress;

    // Base URL for building links in emails.
    // Configured in application.yaml as app.base-url so it works in all environments:
    //   dev  → http://localhost:8080
    //   prod → https://familyhub.example.com
    @Value("${app.base-url}")
    private String baseUrl;

    // Sends a password reset link to the given email address.
    // The token is embedded in the URL — the recipient clicks it to open the reset form.
    public void sendPasswordReset(String toEmail, String token) {
        String resetUrl = baseUrl + "/reset-password?token=" + token;

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

    // Sends a day-before reminder email for an upcoming event.
    // Called by ScheduledJobService at 07:00 for events starting tomorrow.
    public void sendEventReminder(String toEmail, String recipientName, String eventTitle, String startsAt) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Family Hub — Event reminder: " + eventTitle);
        message.setText(
                "Hi " + recipientName + ",\n\n" +
                "Just a reminder — your family has an event coming up tomorrow:\n\n" +
                "  \"" + eventTitle + "\"\n" +
                "  Starts at: " + startsAt + "\n\n" +
                "Log in to Family Hub to see all the details.\n\n" +
                "— Family Hub"
        );

        mailSender.send(message);
        log.info("Event reminder email sent to: {}", toEmail);
    }

    // Notifies a user by email when a task is assigned to them.
    // taskTitle and assignerName give the recipient enough context without opening the app.
    public void sendTaskAssigned(String toEmail, String recipientName, String taskTitle, String assignerName) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Family Hub — New task assigned to you");
        message.setText(
                "Hi " + recipientName + ",\n\n" +
                assignerName + " assigned you a new task:\n\n" +
                "  \"" + taskTitle + "\"\n\n" +
                "Log in to Family Hub to view the details and update the status.\n\n" +
                "— Family Hub"
        );

        mailSender.send(message);
        log.info("Task assigned email sent to: {}", toEmail);
    }
}
