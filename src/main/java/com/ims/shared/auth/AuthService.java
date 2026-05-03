package com.ims.shared.auth;

import com.ims.dto.request.ChangePasswordRequest;
import com.ims.dto.request.ForgotPasswordRequest;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.request.MfaRequest;
import com.ims.dto.request.ResetPasswordRequest;
import com.ims.dto.response.LoginResponse;
import com.ims.model.Tenant;
import com.ims.model.TenantStatus;
import com.ims.model.User;
import com.ims.model.UserRole;
import org.springframework.retry.annotation.Retryable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import com.ims.platform.repository.TenantRepository;
import com.ims.shared.email.EmailService;
import com.ims.shared.exception.UnauthorizedAccessException;
import com.ims.shared.exception.UnauthorizedException;
import com.ims.shared.metrics.BusinessMetrics;
import com.ims.shared.rbac.PermissionService;
import com.ims.tenant.repository.UserRepository;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.persistence.EntityNotFoundException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

  private static final int LOGOUT_EXPIRY_HOURS = 24;
  private static final int RESET_TOKEN_EXPIRY_MINUTES = 15;
  private static final int VERIFICATION_TOKEN_EXPIRY_MINUTES = 15;
  private static final int MAX_FAILED_ATTEMPTS = 5;
  private static final int LOCKOUT_DURATION_MINUTES = 15;

  private static final long IMPERSONATION_ACCESS_TTL_SECONDS = 600L;
  private static final long IMPERSONATION_REFRESH_TTL_SECONDS = 3600L;

  private final UserRepository userRepository;
  private final TenantRepository tenantRepository;
  private final JwtUtil jwtUtil;
  private final PasswordEncoder passwordEncoder;
  private final RedisTemplate<String, Object> redisTemplate;
  private final EmailService emailService;
  private final PermissionService permissionService;
  private final TwoFactorAuthService twoFactorAuthService;
  private final BusinessMetrics businessMetrics;

  private static final String MFA_SESSION_PREFIX = "mfa:session:";
  private static final String MFA_SETUP_PREFIX = "mfa:setup:";
  private static final int MFA_SESSION_TTL_MINUTES = 5;

  // ────────────────────────────────────────────────────────────────────────────
  // Public Entry Points (Establish Context before Transaction)
  // ────────────────────────────────────────────────────────────────────────────

  @RateLimiter(name = "login")
  public LoginResponse login(LoginRequest request) {
    TenantContext.setTenantId(TenantContext.PLATFORM_TENANT_ID);
    try {
      return executeLogin(request);
    } finally {
      TenantContext.clear();
    }
  }

  public LoginResponse platformLogin(LoginRequest request) {
    TenantContext.setTenantId(TenantContext.PLATFORM_TENANT_ID);
    try {
      return executePlatformLogin(request);
    } finally {
      TenantContext.clear();
    }
  }

  public LoginResponse verifyMfa(MfaRequest request) {
    TenantContext.setTenantId(TenantContext.PLATFORM_TENANT_ID);
    try {
      return executeVerifyMfa(request);
    } finally {
      TenantContext.clear();
    }
  }

  @Transactional
  public LoginResponse refresh(String refreshToken) {
    TenantContext.setTenantId(TenantContext.PLATFORM_TENANT_ID);
    try {
      if (!jwtUtil.validateToken(refreshToken))
        throw new UnauthorizedException("Invalid refresh token");
      Long userId = jwtUtil.extractUserId(refreshToken);
      User user = userRepository.findByIdGlobal(userId)
          .orElseThrow(() -> new EntityNotFoundException("User not found"));
      return generateLoginResponse(user);
    } finally {
      TenantContext.clear();
    }
  }

  @Transactional(readOnly = true)
  public Map<String, Boolean> checkEmail(String email) {
    TenantContext.setTenantId(TenantContext.PLATFORM_TENANT_ID);
    try {
      boolean exists = userRepository.findByEmailGlobal(email.trim().toLowerCase()).isPresent();
      return Map.of("available", !exists);
    } finally {
      TenantContext.clear();
    }
  }

  @Transactional(readOnly = true)
  public Map<String, Boolean> checkSlug(String slug) {
    TenantContext.setTenantId(TenantContext.PLATFORM_TENANT_ID);
    try {
      boolean exists = tenantRepository.existsByWorkspaceSlug(slug.trim().toLowerCase());
      return Map.of("available", !exists);
    } finally {
      TenantContext.clear();
    }
  }

  @Transactional(readOnly = true)
  public Map<String, Boolean> checkCompanyCode(String code) {
    TenantContext.setTenantId(TenantContext.PLATFORM_TENANT_ID);
    try {
      boolean exists = tenantRepository.existsByCompanyCode(code.trim().toUpperCase());
      return Map.of("available", !exists);
    } finally {
      TenantContext.clear();
    }
  }

  @Transactional
  public Map<String, String> verifyEmail(String token, String email) {
    TenantContext.setTenantId(TenantContext.PLATFORM_TENANT_ID);
    try {
      User user = userRepository.findByEmailGlobal(email.trim().toLowerCase())
          .orElseThrow(() -> new IllegalArgumentException("Invalid verification token"));
      if (user.getVerificationToken() == null || !passwordEncoder.matches(token, user.getVerificationToken())) {
        throw new IllegalArgumentException("Invalid verification token");
      }
      if (user.getVerificationTokenExpiry() == null || LocalDateTime.now().isAfter(user.getVerificationTokenExpiry())) {
        throw new IllegalArgumentException("Verification token has expired");
      }
      user.setIsVerified(true);
      user.setVerificationToken(null);
      user.setVerificationTokenExpiry(null);
      saveUserWithContext(user);
      return Map.of("message", "Email verified successfully");
    } finally {
      TenantContext.clear();
    }
  }

  @Transactional
  public Map<String, String> resendVerification(String email) {
    TenantContext.setTenantId(TenantContext.PLATFORM_TENANT_ID);
    try {
      User user = userRepository.findByEmailGlobal(email.trim().toLowerCase())
          .orElseThrow(() -> new EntityNotFoundException("User not found"));
      if (Boolean.TRUE.equals(user.getIsVerified()))
        throw new IllegalArgumentException("Email already verified");
      String token = UUID.randomUUID().toString();
      user.setVerificationToken(passwordEncoder.encode(token));
      user.setVerificationTokenExpiry(LocalDateTime.now().plusMinutes(VERIFICATION_TOKEN_EXPIRY_MINUTES));
      saveUserWithContext(user);
      emailService.sendVerificationEmail(user.getEmail(), token);
      return Map.of("message", "Verification email sent");
    } finally {
      TenantContext.clear();
    }
  }

  // ────────────────────────────────────────────────────────────────────────────
  // Transactional Implementations
  // ────────────────────────────────────────────────────────────────────────────

  @Transactional
  @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3)
  public LoginResponse executeLogin(LoginRequest request) {
    User user = userRepository.findByEmailGlobal(request.getEmail())
        .orElseThrow(() -> new EntityNotFoundException("Invalid email or password"));

    if (user.getLockoutUntil() != null && LocalDateTime.now().isBefore(user.getLockoutUntil())) {
      throw new UnauthorizedException("Account is temporarily locked. Try again later.");
    }

    boolean passwordMatches = passwordEncoder.matches(request.getPassword(), user.getPasswordHash());

    if (!passwordMatches) {
      handleFailedLoginAttempt(user);
    }

    // MFA check - if enabled, return MFA required response
    if (Boolean.TRUE.equals(user.isTwoFactorEnabled()) && user.getTwoFactorSecret() != null) {
      String mfaToken = UUID.randomUUID().toString();
      redisTemplate.opsForValue().set(MFA_SESSION_PREFIX + mfaToken, user.getId(),
          MFA_SESSION_TTL_MINUTES, TimeUnit.MINUTES);
      return LoginResponse.builder()
          .mfaRequired(true)
          .mfaToken(mfaToken)
          .build();
    }

    if (!Boolean.TRUE.equals(user.getIsActive())) {
      throw new IllegalArgumentException("Account is deactivated");
    }

    if ("TENANT".equals(user.getScope()) && !Boolean.TRUE.equals(user.getIsVerified())) {
      throw new IllegalArgumentException("Email not verified. Please verify your email before logging in.");
    }

    userRepository.updateLastLogin(user.getId(), LocalDateTime.now());
    userRepository.resetFailedAttempts(user.getId());
    return finalizeLogin(user, request.getCompanyCode());
  }

  @Transactional
  @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3)
  public LoginResponse executePlatformLogin(LoginRequest request) {
    User user = userRepository.findByEmailGlobal(request.getEmail())
        .orElseThrow(() -> new EntityNotFoundException("Invalid email or password"));

    if (user.getLockoutUntil() != null && LocalDateTime.now().isBefore(user.getLockoutUntil())) {
      throw new UnauthorizedException("Account is temporarily locked.");
    }

    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      handleFailedLoginAttempt(user);
    }

    if (!"PLATFORM".equals(user.getScope())) {
      throw new IllegalArgumentException("Only platform administrators can log in here");
    }

    userRepository.updateLastLogin(user.getId(), LocalDateTime.now());
    userRepository.resetFailedAttempts(user.getId());
    return generateLoginResponse(user);
  }

  @Transactional
  @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3)
  public LoginResponse executeVerifyMfa(MfaRequest request) {
    String mfaToken = request.getMfaToken();
    Long userId = (Long) redisTemplate.opsForValue().get(MFA_SESSION_PREFIX + mfaToken);

    if (userId == null)
      throw new UnauthorizedException("MFA session expired or invalid");

    User user = userRepository.findByIdGlobal(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));

    boolean verified = false;
    try {
      verified = twoFactorAuthService.verifyCode(user.getTwoFactorSecret(), Integer.parseInt(request.getCode()));
    } catch (NumberFormatException e) {
      String backupCodes = user.getBackupCodes();
      if (backupCodes != null && backupCodes.contains(request.getCode().toUpperCase())) {
        verified = true;
        user.setBackupCodes(backupCodes.replace(request.getCode().toUpperCase(), "").replace(",,", ","));
      }
    }

    if (!verified)
      throw new IllegalArgumentException("Invalid verification code");

    redisTemplate.delete(MFA_SESSION_PREFIX + mfaToken);
    userRepository.updateLastLogin(userId, LocalDateTime.now());
    return generateLoginResponse(user);
  }

  private void handleFailedLoginAttempt(User user) {
    userRepository.recordFailedAttempt(user.getId(), MAX_FAILED_ATTEMPTS,
        LocalDateTime.now().plusMinutes(LOCKOUT_DURATION_MINUTES));
    businessMetrics.incrementLoginFailures();

    User updatedUser = userRepository.findByIdGlobal(user.getId()).orElseThrow();
    if (updatedUser.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
      throw new UnauthorizedException("Account is temporarily locked due to multiple failed attempts.");
    }
    throw new IllegalArgumentException("Invalid email or password");
  }

  private void saveUserWithContext(User user) {
    Long originalContext = TenantContext.getTenantId();
    Long userTenantId = user.getTenantId();
    TenantContext.setTenantId(userTenantId != null ? userTenantId : TenantContext.PLATFORM_TENANT_ID);
    try {
      userRepository.save(user);
    } finally {
      if (originalContext != null) {
        TenantContext.setTenantId(originalContext);
      } else {
        TenantContext.clear();
      }
    }
  }

  private LoginResponse finalizeLogin(User user, String companyCode) {
    if (companyCode != null && !companyCode.isBlank()) {
      Tenant tenant = tenantRepository.findByCompanyCode(companyCode)
          .orElseThrow(() -> new EntityNotFoundException("Invalid company code"));
      if (!Objects.equals(user.getTenantId(), tenant.getId()))
        throw new IllegalArgumentException("User does not belong to this company");
    } else if (!"PLATFORM".equals(user.getScope())) {
      throw new IllegalArgumentException("Company code is required for business login");
    }
    return generateLoginResponse(user);
  }

  private LoginResponse generateLoginResponse(User user) {
    Long tenantId = user.getTenantId();
    String scope = user.getScope();
    // Establishing context for permissions and role fetch
    Long previousTenant = TenantContext.getTenantId();
    TenantContext.setTenantId(tenantId != null ? tenantId : TenantContext.PLATFORM_TENANT_ID);

    try {
      String businessType = null;
      if ("TENANT".equals(scope) && tenantId != null) {
        businessType = tenantRepository.findById(tenantId).map(Tenant::getBusinessType).orElse(null);
      }

      Set<String> permissions = permissionService.getUserPermissions(user.getId(), tenantId);
      System.out.println("DEBUG: permissions for user " + user.getId() + " tenantId=" + tenantId + ": " + permissions);
      String roleName = userRepository.findRoleNameByUserId(user.getId()).orElse(null);
      UserRole roleEnum = (roleName != null) ? UserRole.valueOf(roleName) : null;

      String accessToken = jwtUtil.generateToken(user.getId(), tenantId != null ? tenantId : -1L, roleEnum, scope,
          businessType != null ? businessType : "NONE", Boolean.TRUE.equals(user.getIsPlatformUser()), permissions);
      String refreshToken = jwtUtil.generateRefreshToken(user.getId(), tenantId != null ? tenantId : -1L, roleEnum,
          scope,
          businessType != null ? businessType : "NONE", Boolean.TRUE.equals(user.getIsPlatformUser()), permissions);

      LoginResponse.LoginResponseBuilder builder = LoginResponse.builder().accessToken(accessToken)
          .refreshToken(refreshToken).expiresIn(jwtUtil.getExpirySeconds())
          .user(LoginResponse.UserResponse.builder().id(user.getId().toString()).name(user.getName())
              .email(user.getEmail()).role(roleName).scope(user.getScope())
              .isPlatformUser(Boolean.TRUE.equals(user.getIsPlatformUser())).build());

      if (tenantId != null) {
        tenantRepository.findById(tenantId).ifPresent(t -> {
          builder
              .tenant(LoginResponse.TenantResponse.builder().id(t.getId()).name(t.getName()).type(t.getBusinessType())
                  .companyCode(t.getCompanyCode()).workspaceSlug(t.getWorkspaceSlug()).build());
        });
      }
      return builder.build();
    } finally {
      if (previousTenant != null) {
        TenantContext.setTenantId(previousTenant);
      } else {
        TenantContext.clear();
      }
    }
  }

  // ────────────────────────────────────────────────────────────────────────────
  // Other Methods
  // ────────────────────────────────────────────────────────────────────────────

  @Transactional
  public LoginResponse impersonateTenant(Long tenantId) {
    TenantContext.setTenantId(TenantContext.PLATFORM_TENANT_ID);
    try {
      var auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth == null)
        throw new UnauthorizedAccessException("Authentication required");
      Long rootUserId = (Long) auth.getPrincipal();
      Tenant tenant = tenantRepository.findById(tenantId)
          .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
      if (TenantStatus.SUSPENDED.equals(tenant.getStatus()) || TenantStatus.INACTIVE.equals(tenant.getStatus()))
        throw new IllegalStateException("Cannot impersonate a " + tenant.getStatus() + " tenant");
      User targetUser = userRepository.findFirstByTenantIdAndAdminRole(tenantId)
          .orElseThrow(() -> new EntityNotFoundException("No admin user found"));
      Set<String> permissions = permissionService.getUserPermissions(targetUser.getId(), tenantId);
      String sessionId = UUID.randomUUID().toString();
      redisTemplate.opsForValue().set("impersonation:session:" + sessionId, rootUserId.toString(),
          IMPERSONATION_REFRESH_TTL_SECONDS, TimeUnit.SECONDS);
      String accessToken = jwtUtil.generateToken(targetUser.getId(), tenantId, UserRole.TENANT_ADMIN, "TENANT",
          tenant.getBusinessType(), false, permissions, true, rootUserId, sessionId, IMPERSONATION_ACCESS_TTL_SECONDS);
      return LoginResponse.builder().accessToken(accessToken).expiresIn(IMPERSONATION_ACCESS_TTL_SECONDS)
          .user(LoginResponse.UserResponse.builder().id(targetUser.getId().toString()).email(targetUser.getEmail())
              .build())
          .tenant(LoginResponse.TenantResponse.builder().id(tenant.getId()).name(tenant.getName()).build()).build();
    } finally {
      TenantContext.clear();
    }
  }

  public void endImpersonation(String token) {
    if (jwtUtil.extractImpersonation(token)) {
      String sessionId = jwtUtil.extractSessionId(token);
      if (sessionId != null)
        redisTemplate.delete("impersonation:session:" + sessionId);
    }
    logout(token);
  }

  public Map<String, Object> getProfile(Long userId) {
    User user = userRepository.findByIdGlobal(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));
    Map<String, Object> result = new HashMap<>();
    result.put("user",
        Map.of("id", userId, "name", user.getName(), "email", user.getEmail(), "scope", user.getScope()));
    if (user.getTenantId() != null)
      tenantRepository.findById(user.getTenantId())
          .ifPresent(t -> result.put("tenant", Map.of("id", t.getId(), "name", t.getName())));
    return result;
  }

  @Transactional
  public Map<String, String> changePassword(Long userId, ChangePasswordRequest request) {
    User user = userRepository.findByIdGlobal(userId).orElseThrow();
    if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash()))
      throw new IllegalArgumentException("Incorrect password");
    user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
    saveUserWithContext(user);
    return Map.of("message", "Password updated successfully");
  }

  @Transactional
  public Map<String, String> forgotPassword(ForgotPasswordRequest request) {
    TenantContext.setTenantId(TenantContext.PLATFORM_TENANT_ID);
    try {
      User user = userRepository.findByEmailGlobal(request.getEmail().trim().toLowerCase()).orElse(null);
      if (user == null)
        return Map.of("message", "Reset link sent if email exists");
      String rawToken = UUID.randomUUID().toString();
      user.setResetToken(passwordEncoder.encode(rawToken));
      user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(RESET_TOKEN_EXPIRY_MINUTES));
      saveUserWithContext(user);
      emailService.sendPasswordResetEmail(user.getEmail(), rawToken);
      return Map.of("message", "Reset link sent if email exists");
    } finally {
      TenantContext.clear();
    }
  }

  @Transactional
  public Map<String, String> resetPassword(ResetPasswordRequest request) {
    TenantContext.setTenantId(TenantContext.PLATFORM_TENANT_ID);
    try {
      User user = userRepository.findByEmailGlobal(request.getEmail().trim().toLowerCase())
          .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token"));
      if (user.getResetToken() == null || !passwordEncoder.matches(request.getResetToken(), user.getResetToken()))
        throw new IllegalArgumentException("Invalid token");
      if (user.getResetTokenExpiry() == null || LocalDateTime.now().isAfter(user.getResetTokenExpiry()))
        throw new IllegalArgumentException("Expired token");
      user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
      user.setResetToken(null);
      user.setResetTokenExpiry(null);
      saveUserWithContext(user);
      return Map.of("message", "Password reset successfully");
    } finally {
      TenantContext.clear();
    }
  }

  public Map<String, Object> getMyPermissions(Long userId) {
    User user = userRepository.findByIdGlobal(userId).orElseThrow();
    Set<String> perms = permissionService.getUserPermissions(userId, user.getTenantId());
    return Map.of("permissions", perms);
  }

  @Transactional
  public Map<String, Object> setup2FA(Long userId) {
    User user = userRepository.findByIdGlobal(userId).orElseThrow();
    TwoFactorAuthService.TwoFactorSecret secret = twoFactorAuthService.generateNewSecret(user.getEmail());
    redisTemplate.opsForValue().set(MFA_SETUP_PREFIX + userId, secret.secret(), 15, TimeUnit.MINUTES);
    return Map.of("secret", secret.secret(), "qrCodeUrl", secret.qrCodeUrl());
  }

  @Transactional
  public Map<String, Object> enable2FA(Long userId, String code) {
    String secret = (String) redisTemplate.opsForValue().get(MFA_SETUP_PREFIX + userId);
    if (secret == null)
      throw new IllegalArgumentException("2FA setup session expired");
    if (!twoFactorAuthService.verifyCode(secret, Integer.parseInt(code)))
      throw new IllegalArgumentException("Invalid code");
    User user = userRepository.findByIdGlobal(userId).orElseThrow();
    user.setTwoFactorSecret(secret);
    user.setTwoFactorEnabled(true);
    var backupCodes = twoFactorAuthService.generateBackupCodes();
    user.setBackupCodes(String.join(",", backupCodes));
    saveUserWithContext(user);
    redisTemplate.delete(MFA_SETUP_PREFIX + userId);
    return Map.of("message", "2FA enabled", "backupCodes", backupCodes);
  }

  @Transactional
  public Map<String, String> disable2FA(Long userId, String password) {
    User user = userRepository.findByIdGlobal(userId).orElseThrow();
    if (!passwordEncoder.matches(password, user.getPasswordHash()))
      throw new IllegalArgumentException("Incorrect password");
    user.setTwoFactorEnabled(false);
    user.setTwoFactorSecret(null);
    user.setBackupCodes(null);
    saveUserWithContext(user);
    return Map.of("message", "2FA disabled");
  }

  public void logout(String token) {
    String tokenHash = hashToken(token);
    redisTemplate.opsForValue().set("jwt:blacklist:" + tokenHash, "revoked", LOGOUT_EXPIRY_HOURS, TimeUnit.HOURS);
  }

  private String hashToken(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(token.getBytes()));
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
}
