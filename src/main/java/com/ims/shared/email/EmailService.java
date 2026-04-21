package com.ims.shared.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${APP_FRONTEND_URL:http://localhost:3000}")
    private String frontendUrl;

    public void sendPasswordResetEmail(String to, String rawToken) {
        String resetLink = frontendUrl + "/reset-password?token=" + rawToken;
        
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Password Reset Request");
        message.setText("To reset your password, click the link below:\n" + resetLink + 
                        "\n\nThis link will expire in 15 minutes.");
        
        try {
            mailSender.send(message);
            log.info("Password reset email sent to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", to, e);
            // In a real app, you might want to retry or throw a custom exception
        }
    }

    public void sendVerificationEmail(String to, String token) {
        String verificationLink = frontendUrl + "/verify-email?token=" + token + "&email=" + to;
        
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Email Verification");
        message.setText("Please verify your email by clicking the link below:\n" + verificationLink);
        
        try {
            mailSender.send(message);
            log.info("Verification email sent to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send verification email to: {}", to, e);
        }
    }
}
