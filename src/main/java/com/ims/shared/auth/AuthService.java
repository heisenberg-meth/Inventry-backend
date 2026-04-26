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
import com.ims.shared.exception.UnauthorizedException;
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
import java.util.List;
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
  private static final String FAILED_ATTEMPTS_PREFIX = "auth:failed:";
  private static final int MAX_FAILED_ATTEMPTS = 5;
  private static final int LOCKOUT_DURATION_MINUTES = 15;

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
  private final TwoFactorAuthService twoFactorAuthService;
  private final com.ims.shared.metrics.BusinessMetrics businessMetrics;

  private static final String MFA_SESSION_PREFIX = "mfa:session:";
  private static final String MFA_SETUP_PREFIX = "mfa:setup:";
  private static final int MFA_SESSION_TTL_MINUTES = 5;

  @Transactional(readOnly = true)
  public Map<String, Boolean> checkEmail(String email) {
    boolean exists =
        userRepository
            .findByEmailGlobal(Objects.requireNonNull(email).trim().toLowerCase())
            .isPresent();
    Map<String, Boolean> result = Map.of("available", !exists);
    return Objects.requireNonNull(result);
  }

  @Transactional(readOnly = true)
  public Map<String, Boolean> checkSlug(String slug) {
    boolean exists =
        tenantRepository.existsByWorkspaceSlug(Objects.requireNonNull(slug).trim().toLowerCase());
    Map<String, Boolean> result = Map.of("available", !exists);
    return Objects.requireNonNull(result);
  }

  @Transactional(readOnly = true)
  public Map<String, Boolean> checkCompanyCode(String code) {
    boolean exists =
        tenantRepository.existsByCompanyCode(Objects.requireNonNull(code).trim().toUpperCase());
    Map<String, Boolean> result = Map.of("exists", exists);
    return Objects.requireNonNull(result);
  }

  @Transactional
  public Map<String, String> verifyEmail(String token, String email) {
    User user =
        userRepository
            .findByEmailGlobal(Objects.requireNonNull(email).trim().toLowerCase())
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
    Map<String, String> result = Map.of("message", "Email verified successfully");
    return Objects.requireNonNull(result);
  }

  @Transactional
  @RateLimiter(name = "resendVerification")
  public Map<String, String> resendVerification(String email) {
    User user =
        userRepository
            .findByEmailGlobal(Objects.requireNonNull(email).trim().toLowerCase())
            .orElse(null);

    // Always return generic success message to prevent email enumeration
    String responseMessage = "Verification email sent if account exists";

    if (user == null) {
      Map<String, String> result = Map.of("message", responseMessage);
      return Objects.requireNonNull(result);
    }

    if (Boolean.TRUE.equals(user.getIsVerified())) {
      Map<String, String> result = Map.of("message", "Email is already verified");
      return Objects.requireNonNull(result);
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
    Map<String, String> result = Map.of("message", responseMessage);
    return Objects.requireNonNull(result);
  }

  @Transactional
  public LoginResponse impersonateTenant(Long tenantId) {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) {
      throw new com.ims.shared.exception.UnauthorizedAccessException("Authentication required");
    }

    Long rootUserId = Objects.requireNonNull((Long) auth.getPrincipal());

    // Block nested impersonation
    if (auth.getDetails() instanceof JwtAuthDetails details && details.isImpersonation()) {
      throw new IllegalStateException("Nested impersonation not allowed");
    }

    Long safeTenantId = Objects.requireNonNull(tenantId);
    Tenant tenant =
        tenantRepository
            .findById(safeTenantId)
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

    auditLogService.logAudit(
        com.ims.shared.audit.AuditAction.ROOT_IMPERSONATION_START,
        com.ims.shared.audit.AuditResource.TENANT,
        safeTenantId,
        String.format("ROOT user %d started impersonation for tenant %d", rootUserId, safeTenantId));

    if (com.ims.model.TenantStatus.SUSPENDED.equals(tenant.getStatus())
        || com.ims.model.TenantStatus.INACTIVE.equals(tenant.getStatus())) {
      throw new IllegalStateException("Cannot impersonate a " + tenant.getStatus() + " tenant");
    }

    User targetUser =
        userRepository
            .findFirstByTenantIdAndAdminRole(safeTenantId)
            .orElseThrow(() -> new EntityNotFoundException("No admin user found for this tenant"));

    String scope = "TENANT"; // Always enforce TENANT scope
    UserRole role = UserRole.ADMIN; // Always enforce ADMIN role inside tenant
    String businessType = tenant.getBusinessType();

    Set<String> permissions =
        permissionService.getUserPermissions(targetUser.getId(), safeTenantId);

    // Impersonation TTLs (see IMPERSONATION_ACCESS_TTL_SECONDS /
    // IMPERSONATION_REFRESH_TTL_SECONDS)
    long impersonationAccessTtl = IMPERSONATION_ACCESS_TTL_SECONDS;
    long impersonationRefreshTtl = IMPERSONATION_REFRESH_TTL_SECONDS;

    // 3. Track session for revocation
    String sessionId = UUID.randomUUID().toString();
    String sessionKey = "impersonation:session:" + sessionId;
    redisTemplate
        .opsForValue()
        .set(
            sessionKey,
            Objects.requireNonNull(rootUserId.toString()),
            impersonationRefreshTtl,
            TimeUnit.SECONDS);

    String accessToken =
        Objects.requireNonNull(
            jwtUtil.generateToken(
                Objects.requireNonNull(targetUser.getId()),
                safeTenantId,
                role,
                scope,
                businessType,
                false,
                Objects.requireNonNull(permissions),
                true,
                rootUserId,
                Objects.requireNonNull(sessionId),
                impersonationAccessTtl));

    String refreshToken =
        Objects.requireNonNull(
            jwtUtil.generateRefreshToken(
                Objects.requireNonNull(targetUser.getId()),
                safeTenantId,
                role,
                scope,
                businessType,
                false,
                Objects.requireNonNull(permissions),
                true,
                rootUserId,
                Objects.requireNonNull(sessionId),
                impersonationRefreshTtl));

    auditLogService.log(
        AuditAction.ROOT_IMPERSONATION_START,
        safeTenantId,
        rootUserId,
        Objects.requireNonNull(Map.of(
            "target_user", targetUser.getEmail(),
            "target_user_id", targetUser.getId(),
            "session_id", sessionId)));

    log.info(
        "ROOT user {} impersonating tenant admin: {} (tenant={})",
        rootUserId,
        targetUser.getEmail(),
        safeTenantId);

    LoginResponse response =
        LoginResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresIn(impersonationAccessTtl)
            .user(
                LoginResponse.UserResponse.builder()
                    .id(Objects.requireNonNull(targetUser.getId()).toString())
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
    return Objects.requireNonNull(response);
  }

  @Transactional
  public LoginResponse platformLogin(LoginRequest request) {
    User user =
        userRepository
            .findByEmailGlobal(request.getEmail())
            .orElseThrow(() -> new EntityNotFoundException("Invalid email or password"));

    String lockoutKey = FAILED_ATTEMPTS_PREFIX + request.getEmail().toLowerCase();
    String attemptsStr = (String) redisTemplate.opsForValue().get(lockoutKey);
    int attempts = attemptsStr != null ? Integer.parseInt(attemptsStr) : 0;

    if (attempts >= MAX_FAILED_ATTEMPTS) {
        throw new com.ims.shared.exception.UnauthorizedException("Account is temporarily locked due to multiple failed attempts. Please try again in " + LOCKOUT_DURATION_MINUTES + " minutes.");
    }

    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      redisTemplate.opsForValue().set(lockoutKey, String.valueOf(attempts + 1), LOCKOUT_DURATION_MINUTES, TimeUnit.MINUTES);
      businessMetrics.incrementLoginFailures();
      throw new IllegalArgumentException("Invalid email or password");
    }

    redisTemplate.delete(lockoutKey);

    if (!Boolean.TRUE.equals(user.getIsActive())) {
      throw new IllegalArgumentException("Account is deactivated");
    }

    if (!"PLATFORM".equals(user.getScope())) {
      throw new IllegalArgumentException("Only platform administrators can log in here");
    }

    Set<String> permissions = permissionService.getUserPermissions(user.getId(), null);

    String scope = Objects.requireNonNull(user.getScope());
    UserRole roleEnum = user.getRole() != null ? UserRole.valueOf(user.getRole().getName()) : null;
    
    String accessToken =
        Objects.requireNonNull(
            jwtUtil.generateToken(
                Objects.requireNonNull(user.getId()),
                TenantContext.PLATFORM_TENANT_ID,
                roleEnum,
                scope,
                "PLATFORM",
                true,
                Objects.requireNonNull(permissions)));
    String refreshToken =
        Objects.requireNonNull(
            jwtUtil.generateRefreshToken(
                Objects.requireNonNull(user.getId()),
                TenantContext.PLATFORM_TENANT_ID,
                roleEnum,
                scope,
                "PLATFORM",
                true,
                Objects.requireNonNull(permissions)));

    // Update last login (atomic update, no version increment)
    userRepository.updateLastLogin(user.getId(), LocalDateTime.now());

    LoginResponse response =
        LoginResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresIn(jwtUtil.getExpirySeconds())
            .user(
                LoginResponse.UserResponse.builder()
                    .id(Objects.requireNonNull(user.getId()).toString())
                    .name(user.getName())
                    .email(user.getEmail())
                    .phone(user.getPhone())
                    .role(user.getRole() != null ? user.getRole().getName() : null)
                    .scope(user.getScope())
                    .isPlatformUser(true)
                    .build())
            .build();
    return Objects.requireNonNull(response);
  }

  public LoginResponse login(LoginRequest request) {
    User user =
        userRepository
            .findByEmailGlobal(request.getEmail())
            .orElseThrow(() -> new EntityNotFoundException("Invalid email or password"));

    String lockoutKey = FAILED_ATTEMPTS_PREFIX + request.getEmail().toLowerCase();
    String attemptsStr = (String) redisTemplate.opsForValue().get(lockoutKey);
    int attempts = attemptsStr != null ? Integer.parseInt(attemptsStr) : 0;

    if (attempts >= MAX_FAILED_ATTEMPTS) {
        throw new com.ims.shared.exception.UnauthorizedException("Account is temporarily locked due to multiple failed attempts. Please try again in " + LOCKOUT_DURATION_MINUTES + " minutes.");
    }

    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      redisTemplate.opsForValue().set(lockoutKey, String.valueOf(attempts + 1), LOCKOUT_DURATION_MINUTES, TimeUnit.MINUTES);
      businessMetrics.incrementLoginFailures();
      throw new IllegalArgumentException("Invalid email or password");
    }

    redisTemplate.delete(lockoutKey);

    if (!Boolean.TRUE.equals(user.getIsActive())) {
      throw new IllegalArgumentException("Account is deactivated");
    }

    if ("TENANT".equals(user.getScope()) && !Boolean.TRUE.equals(user.getIsVerified())) {
      throw new IllegalArgumentException(
          "Email not verified. Please verify your email before logging in.");
    }

    String companyCode = request.getCompanyCode();
    if (companyCode != null && !companyCode.isBlank()) {
      Tenant tenant =
          tenantRepository
              .findByCompanyCode(companyCode)
              .orElseThrow(() -> new EntityNotFoundException("Invalid company code"));
      if (!Objects.equals(user.getTenantId(), tenant.getId())) {
        throw new IllegalArgumentException("User does not belong to this company");
      }
      if (!"TENANT".equals(user.getScope())) {
        throw new IllegalArgumentException("Platform users cannot log in with a company code");
      }
    } else {
      if (!"PLATFORM".equals(user.getScope())) {
        throw new IllegalArgumentException("Company code is required for business login");
      }
    }

    String scope = Objects.requireNonNull(user.getScope());
    String businessType = null;
    Long rawTenantId = user.getTenantId();
    Long safeTenantId = rawTenantId != null ? Objects.requireNonNull(rawTenantId) : null;

    if ("TENANT".equals(scope)) {
      if (safeTenantId != null) {
        Tenant tenant = tenantRepository.findById(safeTenantId).orElse(null);
        if (tenant != null) {
          businessType = tenant.getBusinessType();
        }
      }
    }

    Long previousTenant = TenantContext.getTenantId();
    Set<String> permissions;
    try {
      if (safeTenantId != null) {
        TenantContext.setTenantId(safeTenantId);
      }
      permissions = permissionService.getUserPermissions(user.getId(), safeTenantId);
      userRepository.updateLastLogin(user.getId(), LocalDateTime.now());
    } finally {
      if (previousTenant == null) {
        TenantContext.clear();
      } else {
        TenantContext.setTenantId(previousTenant);
      }
    }

    if (user.isTwoFactorEnabled()) {
        String mfaToken = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(MFA_SESSION_PREFIX + mfaToken, user.getId(), MFA_SESSION_TTL_MINUTES, TimeUnit.MINUTES);
        
        return LoginResponse.builder()
            .mfaRequired(true)
            .mfaToken(mfaToken)
            .build();
    }

    UserRole roleEnum = user.getRole() != null ? UserRole.valueOf(user.getRole().getName()) : null;

    String accessToken =
        Objects.requireNonNull(
            jwtUtil.generateToken(
                Objects.requireNonNull(user.getId()),
                safeTenantId != null ? safeTenantId : TenantContext.PLATFORM_TENANT_ID,
                roleEnum,
                scope,
                businessType != null ? businessType : "NONE",
                Boolean.TRUE.equals(user.getIsPlatformUser()),
                Objects.requireNonNull(permissions)));
    String refreshToken =
        Objects.requireNonNull(
            jwtUtil.generateRefreshToken(
                Objects.requireNonNull(user.getId()),
                safeTenantId != null ? safeTenantId : TenantContext.PLATFORM_TENANT_ID,
                roleEnum,
                scope,
                businessType != null ? businessType : "NONE",
                Boolean.TRUE.equals(user.getIsPlatformUser()),
                Objects.requireNonNull(permissions)));

    LoginResponse.LoginResponseBuilder responseBuilder =
        LoginResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresIn(jwtUtil.getExpirySeconds())
            .user(
                LoginResponse.UserResponse.builder()
                    .id(Objects.requireNonNull(user.getId()).toString())
                    .name(user.getName())
                    .email(user.getEmail())
                    .phone(user.getPhone())
                    .role(user.getRole() != null ? user.getRole().getName() : null)
                    .scope(user.getScope())
                    .isPlatformUser(Boolean.TRUE.equals(user.getIsPlatformUser()))
                    .build());

    if (safeTenantId != null) {
      tenantRepository
          .findById(safeTenantId)
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

    if (safeTenantId != null) {
      auditLogService.log(
          AuditAction.LOGIN,
          Objects.requireNonNull(safeTenantId),
          Objects.requireNonNull(user.getId()),
          "User logged in: " + user.getEmail() + " (" + user.getScope() + ")");
    }

    log.info(
        "Login successful: user={} role={} tenant={}",
        user.getEmail(),
        user.getRole() != null ? user.getRole().getName() : "NONE",
        safeTenantId);

    LoginResponse response = responseBuilder.build();
    return Objects.requireNonNull(response);
  }

  public void logout(String token) {
    String tokenHash = hashToken(token);
    if (jwtUtil.validateToken(token) && jwtUtil.extractImpersonation(token)) {
      Long rootUserId = jwtUtil.extractImpersonatedBy(token);
      Long tenantId = jwtUtil.extractTenantId(token);
      if (tenantId != null) {
        auditLogService.log(
            AuditAction.ROOT_IMPERSONATION_END,
            Objects.requireNonNull(tenantId),
            Objects.requireNonNull(rootUserId),
            "ROOT user ended impersonation session");
      }
    }

    redisTemplate
        .opsForValue()
        .set("jwt:blacklist:" + tokenHash, "revoked", LOGOUT_EXPIRY_HOURS, TimeUnit.HOURS);
  }

  public void endImpersonation(String token) {
    if (!jwtUtil.validateToken(token) || !jwtUtil.extractImpersonation(token)) {
      throw new IllegalArgumentException("Invalid or non-impersonation token");
    }

    String sessionId = jwtUtil.extractSessionId(token);
    if (sessionId != null) {
      redisTemplate.delete("impersonation:session:" + sessionId);
    }

    Long rootUserId = jwtUtil.extractImpersonatedBy(token);
    Long tenantId = jwtUtil.extractTenantId(token);
    if (tenantId != null) {
      auditLogService.log(
          AuditAction.ROOT_IMPERSONATION_END,
          Objects.requireNonNull(tenantId),
          Objects.requireNonNull(rootUserId),
          Objects.requireNonNull(Map.of("session_id", sessionId != null ? sessionId : "N/A")));
    }
    logout(token);
  }

  public void invalidateAllSessions(Long userId) {
      redisTemplate.opsForValue().set("user:revoked-at:" + userId, String.valueOf(System.currentTimeMillis()), 24, TimeUnit.HOURS);
      log.info("Invalidated all sessions for user {}", userId);
  }

  public LoginResponse refresh(String refreshToken) {
    if (!jwtUtil.validateToken(refreshToken)) {
      throw new IllegalArgumentException("Invalid refresh token");
    }

    String tokenType =
        Objects.requireNonNull(
            jwtUtil.extractAllClaims(refreshToken).get("token_type", String.class));
    if (!"refresh".equals(tokenType)) {
      throw new IllegalArgumentException("Not a refresh token");
    }

    boolean impersonation = jwtUtil.extractImpersonation(refreshToken);
    Long impersonatedBy = jwtUtil.extractImpersonatedBy(refreshToken);
    String sessionId = jwtUtil.extractSessionId(refreshToken);

    if (impersonation) {
      if (impersonatedBy == null) {
        throw new IllegalArgumentException("Invalid impersonation token");
      }
      if (sessionId == null || !redisTemplate.hasKey("impersonation:session:" + sessionId)) {
        throw new IllegalArgumentException("Impersonation session expired or revoked");
      }
      User rootUser =
          userRepository
              .findByIdGlobal(impersonatedBy)
              .orElseThrow(() -> new IllegalArgumentException("Root user no longer exists"));
      if (!rootUser.hasRole(UserRole.ROOT) || !Boolean.TRUE.equals(rootUser.getIsActive())) {
        throw new IllegalArgumentException("Root user no longer authorized for impersonation");
      }
    }

    Long userId = Objects.requireNonNull(jwtUtil.extractUserId(refreshToken));
    User user =
        userRepository
            .findByIdGlobal(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

    String scope = Objects.requireNonNull(impersonation ? "TENANT" : user.getScope());
    UserRole roleEnum = impersonation ? UserRole.ADMIN : (user.getRole() != null ? UserRole.valueOf(user.getRole().getName()) : null);
    
    String businessType = null;
    Long rawTenantId = user.getTenantId();
    Long safeTenantId = rawTenantId != null ? Objects.requireNonNull(rawTenantId) : null;

    if ("TENANT".equals(scope)) {
      if (safeTenantId != null) {
        Tenant tenant = tenantRepository.findById(safeTenantId).orElse(null);
        if (tenant != null) {
          businessType = tenant.getBusinessType();
        }
      }
    }

    Set<String> permissions =
        permissionService.getUserPermissions(user.getId(), safeTenantId);

    long accessTtl = impersonation ? IMPERSONATION_ACCESS_TTL_SECONDS : jwtUtil.getExpirySeconds();
    long refreshTtl =
        impersonation ? IMPERSONATION_REFRESH_TTL_SECONDS : jwtUtil.getRefreshExpirySeconds();

    String newAccessToken =
        Objects.requireNonNull(
            jwtUtil.generateToken(
                Objects.requireNonNull(user.getId()),
                safeTenantId != null ? safeTenantId : TenantContext.PLATFORM_TENANT_ID,
                roleEnum,
                scope,
                businessType != null ? businessType : "NONE",
                Boolean.TRUE.equals(user.getIsPlatformUser()),
                Objects.requireNonNull(permissions),
                impersonation,
                impersonatedBy != null ? impersonatedBy : TenantContext.PLATFORM_TENANT_ID,
                Objects.requireNonNull(sessionId),
                accessTtl));
    String newRefreshToken =
        Objects.requireNonNull(
            jwtUtil.generateRefreshToken(
                Objects.requireNonNull(user.getId()),
                safeTenantId != null ? safeTenantId : TenantContext.PLATFORM_TENANT_ID,
                roleEnum,
                scope,
                businessType != null ? businessType : "NONE",
                Boolean.TRUE.equals(user.getIsPlatformUser()),
                Objects.requireNonNull(permissions),
                impersonation,
                impersonatedBy != null ? impersonatedBy : TenantContext.PLATFORM_TENANT_ID,
                Objects.requireNonNull(sessionId),
                refreshTtl));

    logout(refreshToken);

    LoginResponse.LoginResponseBuilder responseBuilder =
        LoginResponse.builder()
            .accessToken(newAccessToken)
            .refreshToken(newRefreshToken)
            .expiresIn(jwtUtil.getExpirySeconds())
            .user(
                LoginResponse.UserResponse.builder()
                    .id(Objects.requireNonNull(user.getId()).toString())
                    .name(user.getName())
                    .email(user.getEmail())
                    .phone(user.getPhone())
                    .role(user.getRole() != null ? user.getRole().getName() : null)
                    .scope(user.getScope())
                    .isPlatformUser(Boolean.TRUE.equals(user.getIsPlatformUser()))
                    .build());

    if (safeTenantId != null) {
      tenantRepository
          .findById(safeTenantId)
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

    return Objects.requireNonNull(responseBuilder.build());
  }

  public Map<String, Object> getProfile(Long userId) {
    User user =
        userRepository
            .findByIdGlobal(Objects.requireNonNull(userId))
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

    Map<String, Object> userMap = new HashMap<>();
    userMap.put("id", userId);
    userMap.put("name", user.getName());
    userMap.put("email", user.getEmail());
    userMap.put("phone", user.getPhone());
    userMap.put("role", user.getRole() != null ? user.getRole().getName() : null);
    userMap.put("scope", user.getScope());
    userMap.put("isPlatformUser", Boolean.TRUE.equals(user.getIsPlatformUser()));
    userMap.put("isActive", user.getIsActive());
    userMap.put("lastLogin", user.getLastLogin());

    Map<String, Object> result = new HashMap<>();
    result.put("user", userMap);

    if (user.getTenantId() != null) {
      tenantRepository
          .findById(Objects.requireNonNull(user.getTenantId()))
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

  public Map<String, Object> getMyPermissions(Long userId) {
    Long tenantId = TenantContext.getTenantId();
    Set<String> permissions = permissionService.getUserPermissions(userId, tenantId);
    return Objects.requireNonNull(Map.of("permissions", permissions));
  }

  @Transactional
  public Map<String, String> changePassword(
      Long userId, ChangePasswordRequest request) {
    User user =
        userRepository
            .findByIdGlobal(Objects.requireNonNull(userId))
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

    if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
      throw new IllegalArgumentException("Current password is incorrect");
    }

    user.setPasswordHash(Objects.requireNonNull(passwordEncoder.encode(request.getNewPassword())));
    userRepository.save(user);

    auditLogService.logAudit(
        AuditAction.PASSWORD_CHANGE,
        AuditResource.USER,
        Objects.requireNonNull(user.getId()),
        "User changed their password: " + user.getEmail());

    return Objects.requireNonNull(Map.of("message", "Password updated successfully"));
  }

  @Transactional
  public Map<String, String> forgotPassword(ForgotPasswordRequest request) {
    User user =
        userRepository.findByEmailGlobal(request.getEmail().trim().toLowerCase()).orElse(null);

    String responseMessage = "If the email exists, a password reset link has been sent";

    if (user == null) {
      return Objects.requireNonNull(Map.of("message", responseMessage));
    }

    String rawToken = UUID.randomUUID().toString();
    String hashedToken = Objects.requireNonNull(passwordEncoder.encode(rawToken));

    user.setResetToken(hashedToken);
    user.setResetTokenExpiry(Objects.requireNonNull(LocalDateTime.now().plusMinutes(RESET_TOKEN_EXPIRY_MINUTES)));
    userRepository.save(user);

    emailService.sendPasswordResetEmail(user.getEmail(), rawToken);

    return Objects.requireNonNull(Map.of("message", responseMessage));
  }

  @Transactional
  public Map<String, String> resetPassword(ResetPasswordRequest request) {
    User user =
        userRepository
            .findByEmailGlobal(request.getEmail().trim().toLowerCase())
            .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token"));

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

    user.setPasswordHash(Objects.requireNonNull(passwordEncoder.encode(request.getNewPassword())));
    user.setResetToken(null);
    user.setResetTokenExpiry(null);
    userRepository.save(user);

    return Objects.requireNonNull(Map.of("message", "Password reset successfully"));
  }

  @Transactional
  public Map<String, Object> setup2FA(Long userId) {
    User user = userRepository.findByIdGlobal(userId)
        .orElseThrow(() -> new EntityNotFoundException("User not found"));
    
    if (user.isTwoFactorEnabled()) {
        throw new IllegalStateException("2FA is already enabled");
    }

    var secret = twoFactorAuthService.generateNewSecret(user.getEmail());
    redisTemplate.opsForValue().set(MFA_SETUP_PREFIX + userId, secret.secret(), MFA_SESSION_TTL_MINUTES, TimeUnit.MINUTES);
    
    return Map.of(
        "secret", secret.secret(),
        "qrCodeUrl", secret.qrCodeUrl()
    );
  }

  @Transactional
  public Map<String, Object> enable2FA(Long userId, String code) {
    String secret = (String) redisTemplate.opsForValue().get(MFA_SETUP_PREFIX + userId);
    if (secret == null) {
        throw new IllegalStateException("2FA setup not initiated or expired");
    }

    if (!twoFactorAuthService.verifyCode(secret, Integer.parseInt(code))) {
        throw new IllegalArgumentException("Invalid verification code");
    }

    User user = userRepository.findByIdGlobal(userId)
        .orElseThrow(() -> new EntityNotFoundException("User not found"));
        
    List<String> backupCodes = twoFactorAuthService.generateBackupCodes();
    
    user.setTwoFactorEnabled(true);
    user.setTwoFactorSecret(secret);
    user.setBackupCodes(String.join(",", backupCodes));
    userRepository.save(user);
    
    redisTemplate.delete(MFA_SETUP_PREFIX + userId);
    
    return Map.of(
        "message", "2FA enabled successfully",
        "backupCodes", backupCodes
    );
  }

  @Transactional
  public Map<String, String> disable2FA(Long userId, String currentPassword) {
    User user = userRepository.findByIdGlobal(userId)
        .orElseThrow(() -> new EntityNotFoundException("User not found"));
        
    if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
        throw new IllegalArgumentException("Invalid password");
    }

    user.setTwoFactorEnabled(false);
    user.setTwoFactorSecret(null);
    user.setBackupCodes(null);
    userRepository.save(user);
    
    return Map.of("message", "2FA disabled successfully");
  }

  @Transactional
  public LoginResponse verifyMfa(com.ims.dto.request.MfaRequest request) {
    String mfaToken = request.getMfaToken();
    Long userId = (Long) redisTemplate.opsForValue().get(MFA_SESSION_PREFIX + mfaToken);
    
    if (userId == null) {
        throw new UnauthorizedException("MFA session expired or invalid");
    }

    User user = userRepository.findByIdGlobal(userId)
        .orElseThrow(() -> new EntityNotFoundException("User not found"));
        
    boolean verified = false;
    try {
        int code = Integer.parseInt(request.getCode());
        verified = twoFactorAuthService.verifyCode(user.getTwoFactorSecret(), code);
    } catch (NumberFormatException e) {
        // Check backup codes
        String backupCodes = user.getBackupCodes();
        if (backupCodes != null && backupCodes.contains(request.getCode().toUpperCase())) {
            verified = true;
            // Remove used backup code
            String newBackupCodes = backupCodes.replace(request.getCode().toUpperCase(), "").replace(",,", ",");
            user.setBackupCodes(newBackupCodes);
            userRepository.save(user);
        }
    }

    if (!verified) {
        throw new IllegalArgumentException("Invalid verification code");
    }

    redisTemplate.delete(MFA_SESSION_PREFIX + mfaToken);
    
    // Proceed with generating tokens (similar to login logic)
    // For brevity, I'll call a private method that generates the full LoginResponse
    return generateLoginResponse(user);
  }

  private LoginResponse generateLoginResponse(User user) {
      Long safeTenantId = user.getTenantId();
      String scope = user.getScope();
      String businessType = null;
      
      if ("TENANT".equals(scope) && safeTenantId != null) {
          businessType = tenantRepository.findById(safeTenantId).map(Tenant::getBusinessType).orElse(null);
      }
      
      Set<String> permissions = permissionService.getUserPermissions(user.getId(), safeTenantId);
      UserRole roleEnum = user.getRole() != null ? UserRole.valueOf(user.getRole().getName()) : null;

      String accessToken = jwtUtil.generateToken(user.getId(), safeTenantId != null ? safeTenantId : TenantContext.PLATFORM_TENANT_ID, roleEnum, scope, businessType != null ? businessType : "NONE", Boolean.TRUE.equals(user.getIsPlatformUser()), permissions);
      String refreshToken = jwtUtil.generateRefreshToken(user.getId(), safeTenantId != null ? safeTenantId : TenantContext.PLATFORM_TENANT_ID, roleEnum, scope, businessType != null ? businessType : "NONE", Boolean.TRUE.equals(user.getIsPlatformUser()), permissions);

      LoginResponse.LoginResponseBuilder builder = LoginResponse.builder()
          .accessToken(accessToken)
          .refreshToken(refreshToken)
          .expiresIn(jwtUtil.getExpirySeconds())
          .user(LoginResponse.UserResponse.builder()
              .id(user.getId().toString())
              .name(user.getName())
              .email(user.getEmail())
              .phone(user.getPhone())
              .role(user.getRole() != null ? user.getRole().getName() : null)
              .scope(user.getScope())
              .isPlatformUser(Boolean.TRUE.equals(user.getIsPlatformUser()))
              .build());

      if (safeTenantId != null) {
          tenantRepository.findById(safeTenantId).ifPresent(tenant -> {
              builder.tenant(LoginResponse.TenantResponse.builder()
                  .id(tenant.getId())
                  .name(tenant.getName())
                  .type(tenant.getBusinessType())
                  .companyCode(tenant.getCompanyCode())
                  .workspaceSlug(tenant.getWorkspaceSlug())
                  .build());
          });
      }
      
      return builder.build();
  }

  private String hashToken(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(token.getBytes());
      return Objects.requireNonNull(HexFormat.of().formatHex(hash));
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 algorithm not available", e);
    }
  }
}
