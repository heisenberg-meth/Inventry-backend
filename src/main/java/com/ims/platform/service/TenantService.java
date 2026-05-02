package com.ims.platform.service;

import com.ims.dto.request.AssignPlanRequest;
import com.ims.dto.request.CreateTenantRequest;
import com.ims.dto.request.CreateTenantUserRequest;
import com.ims.dto.response.TenantResponse;
import com.ims.dto.response.UserResponse;
import com.ims.model.Subscription;
import com.ims.model.SubscriptionPlan;
import com.ims.model.SubscriptionPlanStatus;
import com.ims.model.SubscriptionStatus;
import com.ims.model.Tenant;
import com.ims.model.TenantStatus;
import com.ims.model.User;
import com.ims.model.UserRole;
import com.ims.platform.repository.SubscriptionPlanRepository;
import com.ims.platform.repository.SubscriptionRepository;
import com.ims.platform.repository.TenantRepository;
import com.ims.shared.audit.AuditAction;
import com.ims.shared.audit.AuditLogService;
import com.ims.shared.auth.TenantInitializationService;
import com.ims.tenant.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {

  /** Fallback subscription duration when a plan does not specify one. */
  private static final int DEFAULT_SUBSCRIPTION_DURATION_DAYS = 30;

  /** Length of the auto-generated tenant-admin password. */
  private static final int GENERATED_PASSWORD_LENGTH = 12;

  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final TenantInitializationService tenantInitializationService;
  private final AuditLogService auditLogService;
  private final SubscriptionRepository subscriptionRepository;
  private final SubscriptionPlanRepository subscriptionPlanRepository;
  private final com.ims.shared.utils.CompanyCodeGenerator companyCodeGenerator;

  public Page<TenantResponse> getAllTenants(Pageable pageable) {
    Page<Tenant> tenants = tenantRepository.findAll(Objects.requireNonNull(pageable));
    return tenants.map(t -> toResponse(Objects.requireNonNull(t)));
  }

  @Cacheable(value = "tenant", key = "#id")
  public TenantResponse getTenantById(Long id) {
    Tenant tmpTenant = tenantRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + id));
    Tenant tenant = Objects.requireNonNull(tmpTenant);
    return toResponse(tenant);
  }

  public boolean isWarehouse(Long tenantId) {
    if (tenantId == null) {
      return false;
    }
    return tenantRepository
        .findById(Objects.requireNonNull(tenantId))
        .map(t -> "WAREHOUSE".equals(t.getBusinessType()))
        .orElse(false);
  }

  @Transactional
  public TenantResponse createTenant(CreateTenantRequest request) {
    String businessName = request.getName();
    String requestedSlug = request.getWorkspaceSlug();
    String companyCode = companyCodeGenerator.generateCode(businessName);

    // Hardened retry loop (PRD §4.5)
    for (int attempt = 0; attempt < 8; attempt++) {
      String slug = (requestedSlug != null && !requestedSlug.isBlank() && attempt == 0)
          ? normalizeSlug(requestedSlug)
          : generateWorkspaceSlug(businessName);

      Tenant tenant = Tenant.builder()
          .name(businessName)
          .workspaceSlug(slug)
          .companyCode(companyCode)
          .businessType(request.getBusinessType())
          .plan(request.getPlan() != null ? request.getPlan() : "FREE")
          .status(TenantStatus.ACTIVE)
          .maxProducts(request.getMaxProducts())
          .maxUsers(request.getMaxUsers())
          .build();

      try {
        Tenant savedTenant = tenantRepository.saveAndFlush(tenant);
        log.info("Tenant created: id={} name={} type={}",
            savedTenant.getId(), savedTenant.getName(), savedTenant.getBusinessType());

        Long tenantIdForAudit = Objects.requireNonNull(savedTenant.getId());
        auditLogService.log(
            AuditAction.CREATE_TENANT,
            tenantIdForAudit,
            com.ims.shared.auth.TenantContext.PLATFORM_TENANT_ID,
            "Created tenant: " + savedTenant.getName());

        return toResponse(savedTenant);
      } catch (org.springframework.dao.DataIntegrityViolationException e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (msg.contains("workspace_slug") || msg.contains("idx_tenants_workspace_slug")) {
          if (requestedSlug != null && !requestedSlug.isBlank() && attempt == 0) {
            throw new com.ims.shared.exception.ConflictException("Workspace URL already taken",
                java.util.Map.of("field", "workspaceSlug"));
          }
          log.warn("Workspace slug collision: {}, retrying...", slug);
          // Loop will pick a new slug in next iteration
        } else if (msg.contains("company_code") || msg.contains("uk_tenants_company_code")) {
          companyCode = companyCodeGenerator.generateCode(businessName);
          log.warn("Company code collision, retrying with: {}", companyCode);
        } else {
          throw e;
        }
      }
    }

    throw new com.ims.shared.exception.ConflictException("Unable to allocate unique tenant identifiers",
        java.util.Map.of("field", "workspaceSlug"));
  }

  private String normalizeSlug(String input) {
    if (input == null)
      return null;
    String base = input.toLowerCase(java.util.Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("(^-|-$)", "");
    return base;
  }

  // PRD §4.5 canonical slug generator
  private String generateWorkspaceSlug(String businessName) {
    if (businessName == null || businessName.isBlank()) {
      return "tenant-" + randomBase36(6);
    }
    String base = businessName.toLowerCase(java.util.Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("(^-|-$)", "");

    if (base.isEmpty())
      base = "tenant";
    if (base.length() > 40) {
      base = base.substring(0, 40).replaceAll("(^-|-$)", "");
      if (base.isEmpty())
        base = "tenant";
    }

    return base + "-" + randomBase36(6);
  }

  private String randomBase36(int len) {
    String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
    java.security.SecureRandom rng = new java.security.SecureRandom();
    char[] suffix = new char[len];
    for (int i = 0; i < len; i++) {
      suffix[i] = alphabet.charAt(rng.nextInt(alphabet.length()));
    }
    return new String(suffix);
  }

  @Transactional
  @CacheEvict(value = "tenant", key = "#id")
  public TenantResponse updateTenant(Long id, CreateTenantRequest request) {
    Tenant tmpTenant = tenantRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + id));
    Tenant tenant = Objects.requireNonNull(tmpTenant);

    if (request.getName() != null) {
      tenant.setName(request.getName());
    }
    if (request.getPlan() != null) {
      tenant.setPlan(request.getPlan());
    }
    if (request.getBusinessType() != null) {
      tenant.setBusinessType(request.getBusinessType());
    }
    if (request.getMaxProducts() != null) {
      tenant.setMaxProducts(request.getMaxProducts());
    }
    if (request.getMaxUsers() != null) {
      tenant.setMaxUsers(request.getMaxUsers());
    }

    Tenant tmpUpdated = tenantRepository.save(tenant);
    Tenant updatedTenant = Objects.requireNonNull(tmpUpdated);
    return toResponse(updatedTenant);
  }

  @Transactional
  @CacheEvict(value = "tenant", key = "#id")
  public void deactivateTenant(Long id) {
    Tenant tmpTenant = tenantRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + id));
    Tenant tenant = Objects.requireNonNull(tmpTenant);
    tenant.setStatus(TenantStatus.INACTIVE);
    tenantRepository.save(tenant);
    log.info("Tenant deactivated: id={}", id);
  }

  @Transactional
  @CacheEvict(value = "tenant", key = "#id")
  public Map<String, String> suspendTenant(Long id) {
    Tenant tmpTenant = tenantRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + id));
    Tenant tenant = Objects.requireNonNull(tmpTenant);
    tenant.setStatus(TenantStatus.SUSPENDED);
    tenantRepository.save(tenant);

    auditLogService.log(
        AuditAction.UPDATE_TENANT_STATUS, id, com.ims.shared.auth.TenantContext.PLATFORM_TENANT_ID,
        "Tenant suspended: " + tenant.getName());
    log.info("Tenant suspended: id={}", id);
    return Map.of("message", "Tenant suspended successfully", "status", "SUSPENDED");
  }

  @Transactional
  @CacheEvict(value = "tenant", key = "#id")
  public Map<String, String> activateTenant(Long id) {
    Tenant tmpTenant = tenantRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + id));
    Tenant tenant = Objects.requireNonNull(tmpTenant);
    tenant.setStatus(TenantStatus.ACTIVE);
    tenantRepository.save(tenant);

    auditLogService.log(
        AuditAction.UPDATE_TENANT_STATUS, id, com.ims.shared.auth.TenantContext.PLATFORM_TENANT_ID,
        "Tenant activated: " + tenant.getName());
    log.info("Tenant activated: id={}", id);
    return Map.of("message", "Tenant activated successfully", "status", "ACTIVE");
  }

  /** List users belonging to a specific tenant with optional search. */
  @Transactional(readOnly = true)
  public Page<UserResponse> getTenantUsers(
      Long tenantId, String search, Pageable pageable) {
    if (!tenantRepository.existsById(tenantId)) {
      throw new EntityNotFoundException("Tenant not found with id: " + tenantId);
    }

    Page<User> users;
    if (search != null && !search.isBlank()) {
      users = userRepository.findByTenantIdAndSearch(tenantId, search, pageable);
    } else {
      users = userRepository.findByTenantIdAndScope(tenantId, pageable);
    }

    return users.map(this::toUserResponse);
  }

  /** Reset a tenant user's password (by platform admin). */
  @Transactional
  public Map<String, String> resetTenantUserPassword(Long userId, String newPassword) {
    User user = userRepository
        .findByIdGlobal(userId)
        .orElseThrow(() -> new EntityNotFoundException("User not found"));

    if (!"TENANT".equals(user.getScope())) {
      throw new IllegalArgumentException("User is not a tenant user");
    }

    String password = (newPassword != null && !newPassword.isBlank()) ? newPassword : generateRandomPassword();

    user.setPasswordHash(Objects.requireNonNull(passwordEncoder.encode(password)));
    user.setResetToken(null);
    user.setResetTokenExpiry(null);
    userRepository.save(user);

    Long tenantIdForAudit = user.getTenantId();
    if (tenantIdForAudit != null) {
      auditLogService.log(
          AuditAction.RESET_TENANT_USER_PASSWORD,
          tenantIdForAudit,
          userId,
          "Reset password for tenant user: " + user.getEmail());
    }

    Map<String, String> response = new HashMap<>();
    response.put("message", "Tenant user password reset successfully");
    if (newPassword == null || newPassword.isBlank()) {
      response.put("newPassword", password);
    }
    return response;
  }

  /** Assign a subscription plan to a tenant. */
  @Transactional
  @CacheEvict(value = "tenant", key = "#tenantId")
  public Map<String, Object> assignPlan(
      Long tenantId, AssignPlanRequest request) {
    Tenant tmpTenant = tenantRepository
        .findById(tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
    Tenant tenant = Objects.requireNonNull(tmpTenant);

    SubscriptionPlan tmpPlan = subscriptionPlanRepository
        .findById(Objects.requireNonNull(request.getPlanId()))
        .orElseThrow(() -> new EntityNotFoundException("Subscription plan not found"));
    SubscriptionPlan plan = Objects.requireNonNull(tmpPlan);

    if (plan.getStatus() != SubscriptionPlanStatus.ACTIVE) {
      throw new IllegalArgumentException("Plan is not active");
    }

    subscriptionRepository
        .findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE)
        .forEach(
            sub -> {
              sub.setStatus(SubscriptionStatus.SUSPENDED);
              subscriptionRepository.save(sub);
            });

    // Update tenant limits
    tenant.setPlan(plan.getName());
    if (plan.getMaxUsers() != null && plan.getMaxUsers() > 0) {
      tenant.setMaxUsers(plan.getMaxUsers());
    }
    if (plan.getMaxProducts() != null && plan.getMaxProducts() > 0) {
      tenant.setMaxProducts(plan.getMaxProducts());
    }
    tenant.setStatus(TenantStatus.ACTIVE);
    tenantRepository.save(tenant);

    // Create new subscription
    int durationDays = plan.getDurationDays() != null
        ? plan.getDurationDays()
        : DEFAULT_SUBSCRIPTION_DURATION_DAYS;
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime endDate = now.plusDays(durationDays);

    Subscription subscription = Objects.requireNonNull(
        Subscription.builder()
            .tenantId(tenantId)
            .plan(plan.getName())
            .status(SubscriptionStatus.ACTIVE)
            .startDate(now)
            .endDate(endDate)
            .build());

    Subscription savedSub = Objects.requireNonNull(subscriptionRepository.save(subscription));

    auditLogService.log(
        AuditAction.ASSIGN_PLAN,
        tenantId,
        com.ims.shared.auth.TenantContext.PLATFORM_TENANT_ID,
        "Assigned plan " + plan.getName() + " to tenant " + tenant.getName());

    Map<String, Object> response = new HashMap<>();
    response.put("message", "Plan assigned successfully");
    response.put("subscriptionId", savedSub.getId());
    response.put("plan", plan.getName());
    response.put("status", savedSub.getStatus());
    response.put("startDate", savedSub.getStartDate());
    response.put("endDate", savedSub.getEndDate());
    return response;
  }

  /** Get tenant subscription info. */
  public Map<String, Object> getSubscription(Long tenantId) {
    Tenant tmpTenant = tenantRepository
        .findById(Objects.requireNonNull(tenantId))
        .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
    Tenant tenant = Objects.requireNonNull(tmpTenant);

    Map<String, Object> response = new HashMap<>();
    response.put("plan", tenant.getPlan());
    response.put("status", tenant.getStatus() != null ? tenant.getStatus().name() : null);

    subscriptionRepository
        .findFirstByTenantIdOrderByCreatedAtDesc(tenantId)
        .ifPresent(
            sub -> {
              response.put("subscriptionStatus", sub.getStatus() != null ? sub.getStatus().name() : null);
              response.put("startDate", sub.getStartDate());
              response.put("endDate", sub.getEndDate());
            });

    return response;
  }

  @Transactional
  public UserResponse createTenantUser(
      Long tenantId, CreateTenantUserRequest request) {
    if (!tenantRepository.existsById(tenantId)) {
      throw new EntityNotFoundException("Tenant not found with id: " + tenantId);
    }

    String email = request.getEmail();
    if (userRepository.existsByEmailGlobal(email)) {
      throw new IllegalArgumentException("Email already in use");
    }

    User user = Objects.requireNonNull(
        User.builder()
            .name(request.getUsername())
            .email(email)
            .passwordHash(Objects.requireNonNull(passwordEncoder.encode(request.getPassword())))
            .role(UserRole.valueOf(request.getRole()))
            .scope(request.getScope())
            .isActive(true)
            .build());

    Tenant tmpTenant = tenantRepository
        .findById(tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
    Tenant tenant = Objects.requireNonNull(tmpTenant);

    if (tenant.getMaxUsers() != null) {
      long currentCount = userRepository.countActiveByTenantId(tenantId);
      if (currentCount >= tenant.getMaxUsers()) {
        throw new IllegalArgumentException("User limit reached (" + tenant.getMaxUsers() + ")");
      }
    }

    tenantInitializationService.createUserForTenant(user, tenantId);

    return UserResponse.builder()
        .id(Objects.requireNonNull(user.getId()))
        .name(user.getName())
        .email(user.getEmail())
        .role(user.getRole() != null ? user.getRole().getName() : null)
        .scope(user.getScope())
        .isActive(user.getIsActive())
        .createdAt(Objects.requireNonNull(user.getCreatedAt()))
        .build();
  }

  @Transactional
  public void hardDeleteTenantUser(Long tenantId, Long userId) {
    User tmpUser = userRepository
        .findByIdGlobal(userId)
        .orElseThrow(() -> new EntityNotFoundException("User not found"));
    User user = Objects.requireNonNull(tmpUser);

    if (!Objects.equals(user.getTenantId(), tenantId)) {
      throw new IllegalArgumentException("User does not belong to this tenant");
    }

    userRepository.delete(user);
    log.info("Platform hard-deleted user: {}", user.getEmail());

    auditLogService.log(
        AuditAction.PLATFORM_DELETE_USER,
        tenantId,
        com.ims.shared.auth.TenantContext.PLATFORM_TENANT_ID,
        "Platform admin hard-deleted user: " + user.getEmail());
  }

  private TenantResponse toResponse(Tenant tenant) {
    return TenantResponse.builder()
        .id(tenant.getId())
        .name(tenant.getName())
        .workspaceSlug(tenant.getWorkspaceSlug())
        .businessType(tenant.getBusinessType())
        .plan(tenant.getPlan())
        .status(tenant.getStatus() != null ? tenant.getStatus().name() : null)
        .maxProducts(tenant.getMaxProducts())
        .maxUsers(tenant.getMaxUsers())
        .createdAt(tenant.getCreatedAt())
        .build();
  }

  private UserResponse toUserResponse(User user) {
    return UserResponse.builder()
        .id(user.getId())
        .name(user.getName())
        .email(user.getEmail())
        .role(user.getRole() != null ? user.getRole().getName() : null)
        .scope(user.getScope())
        .isActive(user.getIsActive())
        .createdAt(user.getCreatedAt())
        .build();
  }

  private String generateRandomPassword() {
    String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$";
    StringBuilder sb = new StringBuilder();
    SecureRandom random = new SecureRandom();
    for (int i = 0; i < GENERATED_PASSWORD_LENGTH; i++) {
      sb.append(chars.charAt(random.nextInt(chars.length())));
    }
    return sb.toString();
  }
}
