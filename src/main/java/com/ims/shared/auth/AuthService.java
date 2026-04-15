package com.ims.shared.auth;

import com.ims.dto.request.ChangePasswordRequest;
import com.ims.dto.request.ForgotPasswordRequest;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.request.ResetPasswordRequest;
import com.ims.dto.response.LoginResponse;
import com.ims.model.Tenant;
import com.ims.model.User;
import com.ims.platform.repository.TenantRepository;
import com.ims.tenant.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

  private static final int LOGOUT_EXPIRY_HOURS = 24;
  private static final int HASH_LOG_LENGTH = 8;
  private static final int RESET_TOKEN_BYTE_LENGTH = 32;
  private static final int RESET_TOKEN_EXPIRY_MINUTES = 15;

  private final UserRepository userRepository;
  private final TenantRepository tenantRepository;
  private final JwtUtil jwtUtil;
  private final PasswordEncoder passwordEncoder;
  private final RedisTemplate<String, Object> redisTemplate;
  private final com.ims.shared.audit.AuditLogService auditLogService;

  @Transactional
  public LoginResponse login(LoginRequest request) {
    User user =
        userRepository
            .findByEmailUnfiltered(request.getEmail())
            .orElseThrow(() -> new EntityNotFoundException("Invalid email or password"));

    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      throw new IllegalArgumentException("Invalid email or password");
    }

    if (!Boolean.TRUE.equals(user.getIsActive())) {
      throw new IllegalArgumentException("Account is deactivated");
    }

    // Validate companyCode
    if (request.getCompanyCode() != null && !request.getCompanyCode().isBlank()) {
      Tenant tenant =
          tenantRepository
              .findByCompanyCode(request.getCompanyCode())
              .orElseThrow(() -> new EntityNotFoundException("Invalid company code"));
      if (!Objects.equals(user.getTenantId(), tenant.getId())) {
        throw new IllegalArgumentException("User does not belong to this company");
      }
      if (!"TENANT".equals(user.getScope())) {
        throw new IllegalArgumentException("Platform users cannot log in with a company code");
      }
    } else {
      // Platform login
      if (!"PLATFORM".equals(user.getScope())) {
        throw new IllegalArgumentException("Company code is required for business login");
      }
    }

    String scope = user.getScope();
    String businessType = null;
    Long tenantId = user.getTenantId();

    if ("TENANT".equals(scope)) {
      if (tenantId != null) {
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant != null) {
          businessType = tenant.getBusinessType();
        }
      }
    }

    String accessToken =
        jwtUtil.generateToken(user.getId(), tenantId, user.getRole(), scope, businessType);
    String refreshToken =
        jwtUtil.generateRefreshToken(user.getId(), tenantId, user.getRole(), scope, businessType);

    // Update last login
    user.setLastLogin(LocalDateTime.now());
    userRepository.save(user);

    LoginResponse.LoginResponseBuilder responseBuilder = LoginResponse.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .expiresIn(jwtUtil.getExpirySeconds())
        .user(
            LoginResponse.UserResponse.builder()
                .id(user.getId().toString())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .scope(user.getScope())
                .build());

    if (tenantId != null) {
      tenantRepository.findById(tenantId).ifPresent(tenant -> {
        responseBuilder.tenant(
            LoginResponse.TenantResponse.builder()
                .id(tenant.getId())
                .name(tenant.getName())
                .type(tenant.getBusinessType())
                .address(tenant.getAddress())
                .gstin(tenant.getGstin())
                .plan(tenant.getPlan())
                .companyCode(tenant.getCompanyCode())
                .workspaceSlug(tenant.getWorkspaceSlug())
                .build());
      });
    }

    auditLogService.log(
        "LOGIN",
        tenantId,
        user.getId(),
        "User logged in: " + user.getEmail() + " (" + user.getScope() + ")");

    log.info(
        "Login successful: user={} role={} tenant={}", user.getEmail(), user.getRole(), tenantId);

    return responseBuilder.build();
  }

  public void logout(String token) {
    String tokenHash = hashToken(token);
    redisTemplate
        .opsForValue()
        .set("jwt:blacklist:" + tokenHash, "revoked", LOGOUT_EXPIRY_HOURS, TimeUnit.HOURS);
    log.info("Token blacklisted: {}", tokenHash.substring(0, HASH_LOG_LENGTH) + "...");
  }

  public LoginResponse refresh(String refreshToken) {
    if (!jwtUtil.validateToken(refreshToken)) {
      throw new IllegalArgumentException("Invalid refresh token");
    }

    String tokenType = jwtUtil.extractAllClaims(refreshToken).get("token_type", String.class);
    if (!"refresh".equals(tokenType)) {
      throw new IllegalArgumentException("Not a refresh token");
    }

    Long userId = jwtUtil.extractUserId(refreshToken);
    User user =
        userRepository
            .findByIdUnfiltered(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

    String scope = user.getScope();
    String businessType = null;
    Long tenantId = user.getTenantId();

    if ("TENANT".equals(scope)) {
      if (tenantId != null) {
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant != null) {
          businessType = tenant.getBusinessType();
        }
      }
    }

    String newAccessToken =
        jwtUtil.generateToken(user.getId(), tenantId, user.getRole(), scope, businessType);
    String newRefreshToken =
        jwtUtil.generateRefreshToken(user.getId(), tenantId, user.getRole(), scope, businessType);

    // Blacklist old refresh token
    logout(refreshToken);

    LoginResponse.LoginResponseBuilder responseBuilder = LoginResponse.builder()
        .accessToken(newAccessToken)
        .refreshToken(newRefreshToken)
        .expiresIn(jwtUtil.getExpirySeconds())
        .user(
            LoginResponse.UserResponse.builder()
                .id(user.getId().toString())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .scope(user.getScope())
                .build());

    if (tenantId != null) {
      tenantRepository.findById(tenantId).ifPresent(tenant -> {
        responseBuilder.tenant(
            LoginResponse.TenantResponse.builder()
                .id(tenant.getId())
                .name(tenant.getName())
                .type(tenant.getBusinessType())
                .address(tenant.getAddress())
                .gstin(tenant.getGstin())
                .plan(tenant.getPlan())
                .companyCode(tenant.getCompanyCode())
                .workspaceSlug(tenant.getWorkspaceSlug())
                .build());
      });
    }

    return responseBuilder.build();
  }

  /**
   * Get current user profile.
   */
  public Map<String, Object> getProfile(Long userId) {
    User user =
        userRepository
            .findByIdUnfiltered(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

    Map<String, Object> result = new HashMap<>();
    
    Map<String, Object> userMap = new HashMap<>();
    userMap.put("id", user.getId());
    userMap.put("name", user.getName());
    userMap.put("email", user.getEmail());
    userMap.put("phone", user.getPhone());
    userMap.put("role", user.getRole());
    userMap.put("scope", user.getScope());
    userMap.put("isActive", user.getIsActive());
    userMap.put("lastLogin", user.getLastLogin());
    
    result.put("user", userMap);

    if (user.getTenantId() != null) {
      tenantRepository.findById(Objects.requireNonNull(user.getTenantId())).ifPresent(tenant -> {
        Map<String, Object> tenantMap = new HashMap<>();
        tenantMap.put("id", tenant.getId());
        tenantMap.put("name", tenant.getName());
        tenantMap.put("type", tenant.getBusinessType());
        tenantMap.put("address", tenant.getAddress());
        tenantMap.put("gstin", tenant.getGstin());
        tenantMap.put("plan", tenant.getPlan());
        tenantMap.put("companyCode", tenant.getCompanyCode());
        tenantMap.put("workspaceSlug", tenant.getWorkspaceSlug());
        result.put("tenant", tenantMap);
      });
    }

    return result;
  }

  /**
   * Change password for currently authenticated user.
   */
  @Transactional
  public Map<String, String> changePassword(Long userId, ChangePasswordRequest request) {
    User user =
        userRepository
            .findByIdUnfiltered(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

    if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
      throw new IllegalArgumentException("Current password is incorrect");
    }

    user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
    userRepository.save(user);

    auditLogService.logAudit(
        "PASSWORD_CHANGE",
        "USER",
        user.getId(),
        "User changed their password: " + user.getEmail());

    log.info("Password changed for user: {}", user.getEmail());
    return Map.of("message", "Password updated successfully");
  }

  /**
   * Generate a password reset token and store it on the user.
   * In production, this token would be emailed. For dev, it's returned directly.
   */
  @Transactional
  public Map<String, String> forgotPassword(ForgotPasswordRequest request) {
    User user = userRepository.findByEmailUnfiltered(request.getEmail()).orElse(null);

    // Always return success (security: don't reveal if email exists)
    if (user == null) {
      return Map.of("message", "Password reset link sent to email");
    }

    byte[] tokenBytes = new byte[RESET_TOKEN_BYTE_LENGTH];
    new SecureRandom().nextBytes(tokenBytes);
    String resetToken = HexFormat.of().formatHex(tokenBytes);

    user.setResetToken(resetToken);
    user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(RESET_TOKEN_EXPIRY_MINUTES));
    userRepository.save(user);

    log.info("Password reset token generated for user: {}", user.getEmail());

    // In production: send email with resetToken
    // For dev/testing, return the token directly
    Map<String, String> response = new HashMap<>();
    response.put("message", "Password reset link sent to email");
    response.put("resetToken", resetToken); // Remove in production
    return response;
  }

  /**
   * Reset password using a previously issued reset token.
   */
  @Transactional
  public Map<String, String> resetPassword(ResetPasswordRequest request) {
    User user =
        userRepository
            .findByResetToken(request.getResetToken())
            .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token"));

    if (user.getResetTokenExpiry() == null
        || LocalDateTime.now().isAfter(user.getResetTokenExpiry())) {
      throw new IllegalArgumentException("Reset token has expired");
    }

    user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
    user.setResetToken(null);
    user.setResetTokenExpiry(null);
    userRepository.save(user);

    auditLogService.logAudit(
        "PASSWORD_RESET",
        "USER",
        user.getId(),
        "User reset their password: " + user.getEmail());

    log.info("Password reset successful for user: {}", user.getEmail());
    return Map.of("message", "Password reset successful");
  }

  /**
   * Get current user's role and permissions summary.
   */
  public Map<String, Object> getMyPermissions(Long userId) {
    User user =
        userRepository
            .findByIdUnfiltered(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

    Map<String, Object> result = new HashMap<>();
    result.put("role", user.getRole());
    result.put("scope", user.getScope());
    return result;
  }

  private String hashToken(String token) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(token.getBytes());
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 not available", e);
    }
  }
}
