package com.ims.shared.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

  private final JavaMailSender mailSender;

  @Value("${APP_FRONTEND_URL:http://localhost:3000}")
  private String frontendUrl;

  @Async
  @Retryable(retryFor = MailException.class, maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
  public void sendPasswordResetEmail(String to, String rawToken) {
    String resetLink = frontendUrl + "/reset-password?token=" + rawToken;

    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(to);
    message.setSubject("Password Reset Request");
    message.setText(
        "To reset your password, click the link below:\n"
            + resetLink
            + "\n\nThis link will expire in 15 minutes.");

    mailSender.send(message);
    log.info("Password reset email sent to: {}", to);
  }

  @Async
  @Retryable(retryFor = MailException.class, maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
  public void sendVerificationEmail(String to, String token) {
    String verificationLink = frontendUrl + "/verify-email?token=" + token + "&email=" + to;

    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(to);
    message.setSubject("Email Verification");
    message.setText("Please verify your email by clicking the link below:\n" + verificationLink);

    mailSender.send(message);
    log.info("Verification email sent to: {}", to);
  }

  @Recover
  public void recoverEmailFailure(MailException ex, String to, String token) {
    log.error("All retries failed for email to {}: {}", to, ex.getMessage());
    // In a production app, we could persist this to a failed_emails table
  }
}
