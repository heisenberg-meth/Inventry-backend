package com.ims.platform.service;

import com.ims.dto.request.AssignPlanRequest;
import com.ims.dto.request.CreateTenantRequest;
import com.ims.dto.request.CreateTenantUserRequest;
import com.ims.dto.response.TenantResponse;
import com.ims.dto.response.UserResponse;
import com.ims.model.Subscription;
import com.ims.model.SubscriptionPlan;
import com.ims.model.Tenant;
import com.ims.model.User;
import com.ims.platform.repository.SubscriptionPlanRepository;
import com.ims.platform.repository.SubscriptionRepository;
import com.ims.platform.repository.TenantRepository;
import com.ims.shared.audit.AuditLogService;
import com.ims.shared.auth.UserCreationService;
import com.ims.tenant.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {

  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final UserCreationService userCreationService;
  private final AuditLogService auditLogService;
  private final SubscriptionRepository subscriptionRepository;
  private final SubscriptionPlanRepository subscriptionPlanRepository;

  public Page<TenantResponse> getAllTenants(@NonNull Pageable pageable) {
    Page<Tenant> tenants = Objects.requireNonNull(tenantRepository.findAll(pageable));
    return tenants.map(this::toResponse);
  }

  @Cacheable(value = "tenant", key = "#id")
  public TenantResponse getTenantById(@NonNull Long id) {
    Tenant tenant =
        tenantRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + id));
    return toResponse(Objects.requireNonNull(tenant));
  }

  @Transactional
  public TenantResponse createTenant(@NonNull CreateTenantRequest request) {
    if (request.getDomain() != null && tenantRepository.existsByDomain(request.getDomain())) {
      throw new IllegalArgumentException("Domain already taken: " + request.getDomain());
    }

    Tenant tenant =
        Tenant.builder()
            .name(request.getName())
            .domain(request.getDomain())
            .businessType(request.getBusinessType())
            .plan(request.getPlan() != null ? request.getPlan() : "FREE")
            .status("ACTIVE")
            .maxProducts(request.getMaxProducts())
            .maxUsers(request.getMaxUsers())
            .build();

    Tenant savedTenant = tenantRepository.save(Objects.requireNonNull(tenant));

    log.info(
        "Tenant created: id={} name={} type={}",
        savedTenant.getId(),
        savedTenant.getName(),
        savedTenant.getBusinessType());

    auditLogService.log(
        "CREATE_TENANT",
        savedTenant.getId(),
        null,
        "Created tenant: " + savedTenant.getName());

    return toResponse(savedTenant);
  }

  @Transactional
  @CacheEvict(value = "tenant", key = "#id")
  public TenantResponse updateTenant(@NonNull Long id, @NonNull CreateTenantRequest request) {
    Tenant tenant =
        tenantRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + id));

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

    Tenant updatedTenant = tenantRepository.save(Objects.requireNonNull(tenant));
    return toResponse(updatedTenant);
  }

  @Transactional
  @CacheEvict(value = "tenant", key = "#id")
  public void deactivateTenant(@NonNull Long id) {
    Tenant tenant =
        tenantRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + id));
    tenant.setStatus("INACTIVE");
    tenantRepository.save(tenant);
    log.info("Tenant deactivated: id={}", id);
  }

  @Transactional
  @CacheEvict(value = "tenant", key = "#id")
  public Map<String, String> suspendTenant(@NonNull Long id) {
    Tenant tenant =
        tenantRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + id));
    tenant.setStatus("SUSPENDED");
    tenantRepository.save(tenant);

    auditLogService.log(
        "UPDATE_TENANT_STATUS", id, null, "Tenant suspended: " + tenant.getName());
    log.info("Tenant suspended: id={}", id);
    return Map.of("message", "Tenant suspended successfully", "status", "SUSPENDED");
  }

  @Transactional
  @CacheEvict(value = "tenant", key = "#id")
  public Map<String, String> activateTenant(@NonNull Long id) {
    Tenant tenant =
        tenantRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + id));
    tenant.setStatus("ACTIVE");
    tenantRepository.save(tenant);

    auditLogService.log(
        "UPDATE_TENANT_STATUS", id, null, "Tenant activated: " + tenant.getName());
    log.info("Tenant activated: id={}", id);
    return Map.of("message", "Tenant activated successfully", "status", "ACTIVE");
  }

  /**
   * List users belonging to a specific tenant with optional search.
   */
  @Transactional(readOnly = true)
  public Page<UserResponse> getTenantUsers(
      @NonNull Long tenantId, String search, @NonNull Pageable pageable) {
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

  /**
   * Reset a tenant user's password (by platform admin).
   */
  @Transactional
  public Map<String, String> resetTenantUserPassword(@NonNull Long userId, String newPassword) {
    User user =
        userRepository
            .findByIdUnfiltered(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

    if (!"TENANT".equals(user.getScope())) {
      throw new IllegalArgumentException("User is not a tenant user");
    }

    String password =
        (newPassword != null && !newPassword.isBlank())
            ? newPassword
            : generateRandomPassword();

    user.setPasswordHash(passwordEncoder.encode(password));
    user.setResetToken(null);
    user.setResetTokenExpiry(null);
    userRepository.save(user);

    auditLogService.log(
        "RESET_TENANT_USER_PASSWORD",
        user.getTenantId(),
        userId,
        "Reset password for tenant user: " + user.getEmail());

    Map<String, String> response = new HashMap<>();
    response.put("message", "Tenant user password reset successfully");
    if (newPassword == null || newPassword.isBlank()) {
      response.put("newPassword", password);
    }
    return response;
  }

  /**
   * Assign a subscription plan to a tenant.
   */
  @Transactional
  @CacheEvict(value = "tenant", key = "#tenantId")
  public Map<String, Object> assignPlan(@NonNull Long tenantId, @NonNull AssignPlanRequest request) {
    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

    SubscriptionPlan plan =
        subscriptionPlanRepository
            .findById(Objects.requireNonNull(request.getPlanId()))
            .orElseThrow(() -> new EntityNotFoundException("Subscription plan not found"));

    if (!"ACTIVE".equals(plan.getStatus())) {
      throw new IllegalArgumentException("Plan is not active");
    }

    // Deactivate current active subscriptions
    subscriptionRepository
        .findByTenantIdAndStatus(tenantId, "ACTIVE")
        .forEach(sub -> {
          sub.setStatus("DEACTIVATED");
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
    tenant.setStatus("ACTIVE");
    tenantRepository.save(tenant);

    // Create new subscription
    int durationDays = plan.getDurationDays() != null ? plan.getDurationDays() : 30;
    LocalDateTime startDate = LocalDateTime.now();
    LocalDateTime endDate = startDate.plusDays(durationDays);

    Subscription subscription =
        Subscription.builder()
            .tenantId(tenantId)
            .plan(plan.getName())
            .status("ACTIVE")
            .startDate(startDate)
            .endDate(endDate)
            .build();
    @SuppressWarnings("null")
    Subscription savedSub = Objects.requireNonNull(subscriptionRepository.save(subscription));

    auditLogService.log(
        "ASSIGN_PLAN",
        tenantId,
        null,
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

  /**
   * Get tenant subscription info.
   */
  public Map<String, Object> getSubscription(@NonNull Long tenantId) {
    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

    Map<String, Object> response = new HashMap<>();
    response.put("plan", tenant.getPlan());
    response.put("status", tenant.getStatus());

    subscriptionRepository
        .findFirstByTenantIdOrderByCreatedAtDesc(tenantId)
        .ifPresent(sub -> {
          response.put("subscriptionStatus", sub.getStatus());
          response.put("startDate", sub.getStartDate());
          response.put("endDate", sub.getEndDate());
        });

    return response;
  }

  @Transactional
  public UserResponse createTenantUser(
      @NonNull Long tenantId, @NonNull CreateTenantUserRequest request) {
    if (!tenantRepository.existsById(tenantId)) {
      throw new EntityNotFoundException("Tenant not found with id: " + tenantId);
    }

    String email = Objects.requireNonNull(request.getEmail(), "Email cannot be null");
    if (userRepository.existsByEmail(email)) {
      throw new IllegalArgumentException("Email already in use");
    }

    User user =
        User.builder()
            .name(request.getUsername())
            .email(email)
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .role(request.getRole())
            .scope(request.getScope())
            .tenantId(tenantId)
            .isActive(true)
            .build();

    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

    if (tenant.getMaxUsers() != null) {
      long currentCount = userRepository.countActiveByTenantId(tenantId);
      if (currentCount >= tenant.getMaxUsers()) {
        throw new IllegalArgumentException(
            "User limit reached (" + tenant.getMaxUsers() + ")");
      }
    }

    userCreationService.createUserForTenant(Objects.requireNonNull(user), tenantId);

    return UserResponse.builder()
        .id(user.getId())
        .name(user.getName())
        .email(user.getEmail())
        .role(user.getRole())
        .scope(user.getScope())
        .isActive(user.getIsActive())
        .createdAt(user.getCreatedAt())
        .build();
  }

  private TenantResponse toResponse(@NonNull Tenant tenant) {
    return TenantResponse.builder()
        .id(tenant.getId())
        .name(tenant.getName())
        .domain(tenant.getDomain())
        .businessType(tenant.getBusinessType())
        .plan(tenant.getPlan())
        .status(tenant.getStatus())
        .maxProducts(tenant.getMaxProducts())
        .maxUsers(tenant.getMaxUsers())
        .createdAt(tenant.getCreatedAt())
        .build();
  }

  private UserResponse toUserResponse(@NonNull User user) {
    return UserResponse.builder()
        .id(user.getId())
        .name(user.getName())
        .email(user.getEmail())
        .role(user.getRole())
        .scope(user.getScope())
        .isActive(user.getIsActive())
        .createdAt(user.getCreatedAt())
        .build();
  }

  private String generateRandomPassword() {
    String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$";
    StringBuilder sb = new StringBuilder();
    java.security.SecureRandom random = new java.security.SecureRandom();
    for (int i = 0; i < 12; i++) {
      sb.append(chars.charAt(random.nextInt(chars.length())));
    }
    return sb.toString();
  }
}