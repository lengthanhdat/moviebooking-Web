package com.movie.movie_booking_api.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String from;

    public void sendPasswordResetEmail(String to, String resetLink) {
        if (mailSender == null) {
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            if (from != null && !from.isEmpty()) {
                message.setFrom(from);
            }
            message.setSubject("Password Reset");
            message.setText("Click the link to reset your password: " + resetLink);
            mailSender.send(message);
        } catch (Exception ignored) {
        }
    }
}