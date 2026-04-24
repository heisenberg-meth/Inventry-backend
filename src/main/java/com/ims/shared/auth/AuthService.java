package com.ims.shared.auth;

import com.ims.dto.request.ChangePasswordRequest;
import com.ims.dto.request.ForgotPasswordRequest;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.request.ResetPasswordRequest;
import com.ims.dto.response.LoginResponse;
import com.ims.model.Tenant;
import com.ims.model.User;
import com.ims.model.UserRole;
import com.ims.platform.repository.TenantRepository;
import com.ims.shared.audit.AuditAction;
import com.ims.shared.audit.AuditLogService;
import com.ims.shared.audit.AuditResource;
import com.ims.shared.email.EmailService;
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
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

  private static final int LOGOUT_EXPIRY_HOURS = 24;
  private static final int HASH_LOG_LENGTH = 8;
  private static final int RESET_TOKEN_EXPIRY_MINUTES = 15;
  private static final int VERIFICATION_TOKEN_EXPIRY_MINUTES = 15;

  /** Access-token TTL (seconds) for ROOT-user tenant impersonation sessions (10 minutes). */
  private static final long IMPERSONATION_ACCESS_TTL_SECONDS = 600L;

  /** Refresh-token TTL (seconds) for ROOT-user tenant impersonation sessions (1 hour). */
  private static final long IMPERSONATION_REFRESH_TTL_SECONDS = 3600L;

  private final UserRepository userRepository;
  private final TenantRepository tenantRepository;
  private final JwtUtil jwtUtil;
  private final PasswordEncoder passwordEncoder;
  private final RedisTemplate<String, Object> redisTemplate;
  private final AuditLogService auditLogService;
  private final EmailService emailService;
  private final com.ims.shared.rbac.PermissionService permissionService;

  @Transactional(readOnly = true)
  public @NonNull Map<String, Boolean> checkEmail(@NonNull String email) {
    boolean exists =
        userRepository
            .findByEmailUnfiltered(Objects.requireNonNull(email).trim().toLowerCase())
            .isPresent();
    return Map.of("available", !exists);
  }

  @Transactional(readOnly = true)
  public @NonNull Map<String, Boolean> checkSlug(@NonNull String slug) {
    boolean exists =
        tenantRepository.existsByWorkspaceSlug(Objects.requireNonNull(slug).trim().toLowerCase());
    return Map.of("available", !exists);
  }

  @Transactional(readOnly = true)
  public @NonNull Map<String, Boolean> checkCompanyCode(@NonNull String code) {
    boolean exists =
        tenantRepository.existsByCompanyCode(Objects.requireNonNull(code).trim().toUpperCase());
    return Map.of("exists", exists);
  }

  @Transactional
  public @NonNull Map<String, String> verifyEmail(@NonNull String token, @NonNull String email) {
    User user =
        userRepository
            .findByEmailUnfiltered(Objects.requireNonNull(email).trim().toLowerCase())
            .orElseThrow(() -> new IllegalArgumentException("Invalid verification token"));

    if (user.getVerificationToken() == null
        || !passwordEncoder.matches(token, user.getVerificationToken())) {
      throw new IllegalArgumentException("Invalid verification token");
    }

    if (user.getVerificationTokenExpiry() == null
        || LocalDateTime.now().isAfter(user.getVerificationTokenExpiry())) {
      throw new IllegalArgumentException("Verification token has expired");
    }

    user.setIsVerified(true);
    user.setVerificationToken(null);
    user.setVerificationTokenExpiry(null);
    userRepository.save(user);

    log.info("Email verified for user: {}", user.getEmail());
    return Map.of("message", "Email verified successfully");
  }

  @Transactional
  @RateLimiter(name = "resendVerification")
  public @NonNull Map<String, String> resendVerification(@NonNull String email) {
    User user =
        userRepository
            .findByEmailUnfiltered(Objects.requireNonNull(email).trim().toLowerCase())
            .orElse(null);

    // Always return generic success message to prevent email enumeration
    String responseMessage = "Verification email sent if account exists";

    if (user == null) {
      return Map.of("message", responseMessage);
    }

    if (Boolean.TRUE.equals(user.getIsVerified())) {
      return Map.of("message", "Email is already verified");
    }

    // Generate and hash new token
    String rawToken = UUID.randomUUID().toString();
    String hashedToken = passwordEncoder.encode(rawToken);

    user.setVerificationToken(hashedToken);
    user.setVerificationTokenExpiry(
        LocalDateTime.now().plusMinutes(VERIFICATION_TOKEN_EXPIRY_MINUTES));
    userRepository.save(user);

    log.info("Verification email resent to: {}", email);
    emailService.sendVerificationEmail(email, rawToken);
    return Map.of("message", responseMessage);
  }

  @Transactional
  public @NonNull LoginResponse impersonateTenant(@NonNull Long tenantId) {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) {
      throw new com.ims.shared.exception.UnauthorizedAccessException("Authentication required");
    }

    Long rootUserId = (Long) auth.getPrincipal();

    // Block nested impersonation
    if (auth.getDetails() instanceof JwtAuthDetails details && details.isImpersonation()) {
      throw new IllegalStateException("Nested impersonation not allowed");
    }

    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

    if (com.ims.model.TenantStatus.SUSPENDED.equals(tenant.getStatus())
        || com.ims.model.TenantStatus.INACTIVE.equals(tenant.getStatus())) {
      throw new IllegalStateException("Cannot impersonate a " + tenant.getStatus() + " tenant");
    }

    User targetUser =
        userRepository
            .findFirstByTenantIdAndAdminRole(tenantId)
            .orElseThrow(() -> new EntityNotFoundException("No admin user found for this tenant"));

    String scope = "TENANT"; // Always enforce TENANT scope
    UserRole role = UserRole.ADMIN; // Always enforce ADMIN role inside tenant
    String businessType = tenant.getBusinessType();

    java.util.Set<String> permissions =
        permissionService.getUserPermissions(targetUser.getId(), tenantId);

    // Impersonation TTLs (see IMPERSONATION_ACCESS_TTL_SECONDS / IMPERSONATION_REFRESH_TTL_SECONDS)
    long impersonationAccessTtl = IMPERSONATION_ACCESS_TTL_SECONDS;
    long impersonationRefreshTtl = IMPERSONATION_REFRESH_TTL_SECONDS;

    String accessToken =
        jwtUtil.generateToken(
            targetUser.getId(),
            tenantId,
            role,
            scope,
            businessType,
            false,
            permissions,
            true,
            rootUserId,
            impersonationAccessTtl);

    String refreshToken =
        jwtUtil.generateRefreshToken(
            targetUser.getId(),
            tenantId,
            role,
            scope,
            businessType,
            false,
            permissions,
            true,
            rootUserId,
            impersonationRefreshTtl);

    auditLogService.log(
        AuditAction.ROOT_IMPERSONATION_START,
        tenantId,
        rootUserId,
        "ROOT user started impersonation of tenant: "
            + tenant.getName()
            + " (targetUser: "
            + targetUser.getEmail()
            + ")");

    log.info(
        "ROOT user {} impersonating tenant admin: {} (tenant={})",
        rootUserId,
        targetUser.getEmail(),
        tenantId);

    return LoginResponse.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .expiresIn(impersonationAccessTtl)
        .user(
            LoginResponse.UserResponse.builder()
                .id(targetUser.getId().toString())
                .name(targetUser.getName())
                .email(targetUser.getEmail())
                .role(role.name())
                .scope(scope)
                .isPlatformUser(false)
                .build())
        .tenant(
            LoginResponse.TenantResponse.builder()
                .id(tenant.getId())
                .name(tenant.getName())
                .type(tenant.getBusinessType())
                .companyCode(tenant.getCompanyCode())
                .workspaceSlug(tenant.getWorkspaceSlug())
                .build())
        .build();
  }

  @Transactional
  public @NonNull LoginResponse platformLogin(@NonNull LoginRequest request) {
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

    if (!"PLATFORM".equals(user.getScope())) {
      throw new IllegalArgumentException("Only platform administrators can log in here");
    }

    java.util.Set<String> permissions = permissionService.getUserPermissions(user.getId(), null);

    String scope = user.getScope();
    String accessToken =
        jwtUtil.generateToken(user.getId(), null, user.getRole(), scope, null, true, permissions);
    String refreshToken =
        jwtUtil.generateRefreshToken(
            user.getId(), null, user.getRole(), scope, null, true, permissions);

    // Update last login (atomic update, no version increment)
    userRepository.updateLastLogin(user.getId(), LocalDateTime.now());

    return LoginResponse.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .expiresIn(jwtUtil.getExpirySeconds())
        .user(
            LoginResponse.UserResponse.builder()
                .id(user.getId().toString())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole().name())
                .scope(user.getScope())
                .isPlatformUser(true)
                .build())
        .build();
  }

  /**
   * Authenticate a user and issue access/refresh tokens.
   *
   * <p>Intentionally NOT annotated {@code @Transactional} at this level: a single outer transaction
   * would pin the Hibernate session to the caller's {@link TenantContext} (set by {@link
   * com.ims.shared.auth.TenantFilter}), but login identifies the user first and only then knows
   * which tenant to switch to. Each downstream repository call (including {@link
   * PermissionService#getUserPermissions} and {@code userRepository.updateLastLogin}) has its own
   * transactional boundary and is invoked under the correct tenant below.
   */
  public @NonNull LoginResponse login(@NonNull LoginRequest request) {
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

    // Check email verification for tenant users
    if ("TENANT".equals(user.getScope()) && !Boolean.TRUE.equals(user.getIsVerified())) {
      throw new IllegalArgumentException(
          "Email not verified. Please verify your email before logging in.");
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

    // Tenant-filtered lookups below (permissionService, lastLogin) must run in the user's
    // tenant context. Incoming /api/auth/login requests have no tenant set because the tenant
    // is only known after identifying the user, so we set it explicitly here.
    Long previousTenant = TenantContext.getTenantId();
    java.util.Set<String> permissions;
    try {
      if (tenantId != null) {
        TenantContext.setTenantId(tenantId);
      }
      permissions = permissionService.getUserPermissions(user.getId(), tenantId);
      // Update last login (atomic update, no version increment)
      userRepository.updateLastLogin(user.getId(), LocalDateTime.now());
    } finally {
      if (previousTenant == null) {
        TenantContext.clear();
      } else {
        TenantContext.setTenantId(previousTenant);
      }
    }

    String accessToken =
        jwtUtil.generateToken(
            user.getId(),
            tenantId,
            user.getRole(),
            scope,
            businessType,
            Boolean.TRUE.equals(user.getIsPlatformUser()),
            permissions);
    String refreshToken =
        jwtUtil.generateRefreshToken(
            user.getId(),
            tenantId,
            user.getRole(),
            scope,
            businessType,
            Boolean.TRUE.equals(user.getIsPlatformUser()),
            permissions);

    LoginResponse.LoginResponseBuilder responseBuilder =
        LoginResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresIn(jwtUtil.getExpirySeconds())
            .user(
                LoginResponse.UserResponse.builder()
                    .id(user.getId().toString())
                    .name(user.getName())
                    .email(user.getEmail())
                    .phone(user.getPhone())
                    .role(user.getRole().name())
                    .scope(user.getScope())
                    .isPlatformUser(Boolean.TRUE.equals(user.getIsPlatformUser()))
                    .build());

    if (tenantId != null) {
      tenantRepository
          .findById(tenantId)
          .ifPresent(
              tenant -> {
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
        AuditAction.LOGIN,
        tenantId,
        user.getId(),
        "User logged in: " + user.getEmail() + " (" + user.getScope() + ")");

    log.info(
        "Login successful: user={} role={} tenant={}", user.getEmail(), user.getRole(), tenantId);

    return responseBuilder.build();
  }

  public void logout(@NonNull String token) {
    String tokenHash = hashToken(token);

    // Log impersonation end if applicable
    if (jwtUtil.validateToken(token) && jwtUtil.extractImpersonation(token)) {
      Long rootUserId = jwtUtil.extractImpersonatedBy(token);
      Long tenantId = jwtUtil.extractTenantId(token);
      auditLogService.log(
          AuditAction.ROOT_IMPERSONATION_END,
          tenantId,
          rootUserId,
          "ROOT user ended impersonation session");
    }

    redisTemplate
        .opsForValue()
        .set("jwt:blacklist:" + tokenHash, "revoked", LOGOUT_EXPIRY_HOURS, TimeUnit.HOURS);
    log.info("Token blacklisted: {}", tokenHash.substring(0, HASH_LOG_LENGTH) + "...");
  }

  public @NonNull LoginResponse refresh(@NonNull String refreshToken) {
    if (!jwtUtil.validateToken(refreshToken)) {
      throw new IllegalArgumentException("Invalid refresh token");
    }

    String tokenType = jwtUtil.extractAllClaims(refreshToken).get("token_type", String.class);
    if (!"refresh".equals(tokenType)) {
      throw new IllegalArgumentException("Not a refresh token");
    }

    boolean impersonation = jwtUtil.extractImpersonation(refreshToken);
    Long impersonatedBy = jwtUtil.extractImpersonatedBy(refreshToken);

    if (impersonation) {
      if (impersonatedBy == null) {
        throw new IllegalArgumentException("Invalid impersonation token");
      }
      User rootUser =
          userRepository
              .findByIdUnfiltered(impersonatedBy)
              .orElseThrow(() -> new IllegalArgumentException("Root user no longer exists"));
      if (rootUser.getRole() != UserRole.ROOT || !Boolean.TRUE.equals(rootUser.getIsActive())) {
        throw new IllegalArgumentException("Root user no longer authorized for impersonation");
      }
    }

    Long userId = jwtUtil.extractUserId(refreshToken);
    User user =
        userRepository
            .findByIdUnfiltered(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

    String scope = impersonation ? "TENANT" : user.getScope();
    UserRole role = impersonation ? UserRole.ADMIN : user.getRole();
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

    java.util.Set<String> permissions =
        permissionService.getUserPermissions(user.getId(), tenantId);

    // Maintain impersonation TTLs on refresh
    long accessTtl = impersonation ? IMPERSONATION_ACCESS_TTL_SECONDS : jwtUtil.getExpirySeconds();
    long refreshTtl =
        impersonation ? IMPERSONATION_REFRESH_TTL_SECONDS : jwtUtil.getRefreshExpirySeconds();

    String newAccessToken =
        jwtUtil.generateToken(
            user.getId(),
            tenantId,
            role,
            scope,
            businessType,
            Boolean.TRUE.equals(user.getIsPlatformUser()),
            permissions,
            impersonation,
            impersonatedBy,
            accessTtl);
    String newRefreshToken =
        jwtUtil.generateRefreshToken(
            user.getId(),
            tenantId,
            role,
            scope,
            businessType,
            Boolean.TRUE.equals(user.getIsPlatformUser()),
            permissions,
            impersonation,
            impersonatedBy,
            refreshTtl);

    // Blacklist old refresh token
    logout(refreshToken);

    LoginResponse.LoginResponseBuilder responseBuilder =
        LoginResponse.builder()
            .accessToken(newAccessToken)
            .refreshToken(newRefreshToken)
            .expiresIn(jwtUtil.getExpirySeconds())
            .user(
                LoginResponse.UserResponse.builder()
                    .id(user.getId().toString())
                    .name(user.getName())
                    .email(user.getEmail())
                    .phone(user.getPhone())
                    .role(user.getRole().name())
                    .scope(user.getScope())
                    .isPlatformUser(Boolean.TRUE.equals(user.getIsPlatformUser()))
                    .build());

    if (tenantId != null) {
      tenantRepository
          .findById(tenantId)
          .ifPresent(
              tenant -> {
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

  /** Get current user profile. */
  public @NonNull Map<String, Object> getProfile(@NonNull Long userId) {
    User user =
        userRepository
            .findByIdUnfiltered(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

    Map<String, Object> userMap = new HashMap<>();
    userMap.put("id", user.getId());
    userMap.put("name", user.getName());
    userMap.put("email", user.getEmail());
    userMap.put("phone", user.getPhone());
    userMap.put("role", user.getRole());
    userMap.put("scope", user.getScope());
    userMap.put("isPlatformUser", Boolean.TRUE.equals(user.getIsPlatformUser()));
    userMap.put("isActive", user.getIsActive());
    userMap.put("lastLogin", user.getLastLogin());

    Map<String, Object> result = new HashMap<>();
    result.put("user", userMap);

    if (user.getTenantId() != null) {
      tenantRepository
          .findById(user.getTenantId())
          .ifPresent(
              tenant -> {
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

  /** Change password for currently authenticated user. */
  @Transactional
  public @NonNull Map<String, String> changePassword(
      @NonNull Long userId, @NonNull ChangePasswordRequest request) {
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
        AuditAction.PASSWORD_CHANGE,
        AuditResource.USER,
        user.getId(),
        "User changed their password: " + user.getEmail());

    log.info("Password changed for user: {}", user.getEmail());
    return Map.of("message", "Password updated successfully");
  }

  /**
   * Generate a password reset token and store it on the user. In production, this token would be
   * emailed. For dev, it's returned directly.
   */
  @Transactional
  public @NonNull Map<String, String> forgotPassword(@NonNull ForgotPasswordRequest request) {
    User user =
        userRepository.findByEmailUnfiltered(request.getEmail().trim().toLowerCase()).orElse(null);

    // Always return generic success message to prevent email enumeration
    String responseMessage = "If the email exists, a password reset link has been sent";

    if (user == null) {
      return Map.of("message", responseMessage);
    }

    String rawToken = UUID.randomUUID().toString();
    String hashedToken = passwordEncoder.encode(rawToken);

    user.setResetToken(hashedToken);
    user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(RESET_TOKEN_EXPIRY_MINUTES));
    userRepository.save(user);

    log.info("Password reset token generated and hashed for user: {}", user.getEmail());

    emailService.sendPasswordResetEmail(user.getEmail(), rawToken);

    return Map.of("message", responseMessage);
  }

  /** Reset password using a previously issued reset token. */
  @Transactional
  public @NonNull Map<String, String> resetPassword(@NonNull ResetPasswordRequest request) {
    User user =
        userRepository
            .findByEmailUnfiltered(request.getEmail().trim().toLowerCase())
            .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token"));

    // timing attack protection: always run passwordEncoder.matches
    String storedToken =
        user.getResetToken() != null
            ? user.getResetToken()
            : "$2a$10$invalid-placeholder-hash-prevents-timing";
    boolean tokenMatches = passwordEncoder.matches(request.getResetToken(), storedToken);
    boolean notExpired =
        user.getResetTokenExpiry() != null
            && !LocalDateTime.now().isAfter(user.getResetTokenExpiry());

    if (!tokenMatches || !notExpired) {
      throw new IllegalArgumentException("Invalid or expired reset token");
    }

    user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
    user.setResetToken(null);
    user.setResetTokenExpiry(null);
    userRepository.save(user);

    auditLogService.logAudit(
        AuditAction.PASSWORD_RESET,
        AuditResource.USER,
        user.getId(),
        "User reset their password: " + user.getEmail());

    log.info("Password reset successful for user: {}", user.getEmail());
    return Map.of("message", "Password reset successful");
  }

  /** Get current user's role and permissions summary. */
  public @NonNull Map<String, Object> getMyPermissions(@NonNull Long userId) {
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
