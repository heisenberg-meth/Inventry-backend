package com.ims.shared.auth;

import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.SignupResponse;
import com.ims.model.Subscription;
import com.ims.model.SubscriptionPlan;
import com.ims.model.SubscriptionStatus;
import com.ims.model.Tenant;
import com.ims.model.TenantStatus;
import com.ims.model.User;
import com.ims.platform.repository.SubscriptionPlanRepository;
import com.ims.platform.repository.SubscriptionRepository;
import com.ims.platform.repository.TenantRepository;
import com.ims.shared.audit.AuditAction;
import com.ims.shared.audit.AuditLogService;
import com.ims.shared.exception.ConflictException;
import com.ims.shared.exception.ServiceUnavailableException;
import com.ims.shared.utils.CompanyCodeGenerator;
import com.ims.tenant.repository.UserRepository;
import com.ims.tenant.service.TenantSettingsService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class SignupService {

  private static final int MAX_SLUG_RETRIES = 3;
  private static final int MAX_CODE_RETRIES = 5;

  private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
  private static final Pattern TRIM_DASH = Pattern.compile("(^-|-$)");
  private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
  private static final SecureRandom RNG = new SecureRandom();

  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final TenantInitializationService tenantInitializationService;
  private final CompanyCodeGenerator companyCodeGenerator;
  private final SubscriptionRepository subscriptionRepository;
  private final AuditLogService auditLogService;
  private final TenantSettingsService tenantSettingsService;
  private final com.ims.platform.service.SystemConfigService systemConfigService;
  private final SubscriptionPlanRepository subscriptionPlanRepository;
  private final com.ims.shared.metrics.BusinessMetrics businessMetrics;

  // Micrometer metrics (PRD §11.1)
  private final Counter signupSuccessCounter;
  private final Counter signupFailureCounter;
  private final Counter duplicateRetryCounter;
  private final Timer signupLatencyTimer;

  public SignupService(
      TenantRepository tenantRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      TenantInitializationService tenantInitializationService,
      CompanyCodeGenerator companyCodeGenerator,
      SubscriptionRepository subscriptionRepository,
      AuditLogService auditLogService,
      TenantSettingsService tenantSettingsService,
      com.ims.platform.service.SystemConfigService systemConfigService,
      SubscriptionPlanRepository subscriptionPlanRepository,
      MeterRegistry meterRegistry,
      com.ims.shared.metrics.BusinessMetrics businessMetrics) {
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.tenantInitializationService = tenantInitializationService;
    this.companyCodeGenerator = companyCodeGenerator;
    this.subscriptionRepository = subscriptionRepository;
    this.auditLogService = auditLogService;
    this.tenantSettingsService = tenantSettingsService;
    this.systemConfigService = systemConfigService;
    this.subscriptionPlanRepository = subscriptionPlanRepository;
    this.businessMetrics = businessMetrics;

    this.signupSuccessCounter = Counter.builder("signup.success.count")
        .description("Total successful signups")
        .register(meterRegistry);
    this.signupFailureCounter = Counter.builder("signup.failure.count")
        .description("Total failed signups")
        .register(meterRegistry);
    this.duplicateRetryCounter = Counter.builder("signup.duplicate.retry.count")
        .description("Slug/code collision retries during signup")
        .register(meterRegistry);
    this.signupLatencyTimer = Timer.builder("signup.latency")
        .description("Signup request latency")
        .register(meterRegistry);
  }

  /**
   * Main entry point for signup. Handles slug/code resolution and retries
   * OUTSIDE the transaction to avoid marking the transaction as rollback-only
   * on unique constraint violations.
   */
  public SignupResponse signup(SignupRequest request) {
    return signupLatencyTimer.record(() -> {
      // 0. Idempotency Check (PRD §4.3) - Advisory check before transaction
      if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
        var existing = tenantRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
          log.info("Signup: Returning existing tenant for idempotency key: {}", request.getIdempotencyKey());
          Tenant t = existing.get();
          return new SignupResponse("Signup already completed", t.getCompanyCode(), t.getWorkspaceSlug());
        }
      }

      String normalizedEmail = request.getOwnerEmail().trim().toLowerCase();
      if (userRepository.findByEmailGlobal(normalizedEmail).isPresent()) {
        throw new ConflictException("Email already registered", Map.of("field", "ownerEmail"));
      }

      // 1. Resolve slug and code with existence checks
      String workspaceSlug = resolveUniqueSlug(request);
      String companyCode = resolveUniqueCompanyCode(request.getBusinessName());

      // 2. Execute actual signup in a transaction
      // FR-04-A: Set platform context before starting transaction to bypass
      // TenantEnforcementAspect
      TenantContext.setTenantId(TenantContext.PLATFORM_TENANT_ID);
      try {
        return executeSignup(request, workspaceSlug, companyCode);
      } finally {
        TenantContext.clear();
      }
    });
  }

  @Transactional
  public SignupResponse executeSignup(SignupRequest request, String workspaceSlug, String companyCode) {
    String normalizedEmail = request.getOwnerEmail().trim().toLowerCase();
    String businessName = request.getBusinessName();

    // Re-verify email inside transaction for strict atomicity
    if (userRepository.findByEmailGlobal(normalizedEmail).isPresent()) {
      throw new ConflictException("Email already registered", Map.of("field", "ownerEmail"));
    }

    // Create the tenant
    Tenant tenant = Tenant.builder()
        .name(businessName)
        .businessType(request.getBusinessType())
        .workspaceSlug(workspaceSlug)
        .companyCode(companyCode)
        .status(TenantStatus.ACTIVE)
        .plan("TRIAL")
        .address(request.getAddress())
        .gstin(request.getGstin())
        .idempotencyKey(request.getIdempotencyKey())
        .build();

    tenant = tenantRepository.save(tenant);
    Long tenantId = tenant.getId();
    String tenantName = tenant.getName();

    Long previousTenant = TenantContext.getTenantId();
    try {
      // Set context IMMEDIATELY after save so subsequent operations have it
      TenantContext.setTenantId(tenantId);

      // 5. Create admin user (PRD §4.1 step 4)
      User user = User.builder()
          .name(request.getOwnerName())
          .email(Objects.requireNonNull(normalizedEmail))
          .phone(request.getOwnerPhone())
          .passwordHash(Objects.requireNonNull(
              passwordEncoder.encode(request.getPassword())))
          .scope("TENANT")
          .isActive(true)
          .build();

      // 6. Initialize tenant: seeds roles, creates user, sends email (PRD §4.1 steps
      // 4-5, §7)
      tenantInitializationService.initializeTenant(user, tenantId, tenantName);

      // 7. Create default subscription (PRD §4.4, §4.4-C)
      int trialDays = systemConfigService.getInt("TRIAL_DAYS", 7);
      if (trialDays < 0) {
        throw new IllegalStateException("Invalid TRIAL_DAYS config: " + trialDays);
      }

      SubscriptionPlan plan = subscriptionPlanRepository.findDefaultPlan()
          .orElseThrow(() -> new IllegalStateException("No active default subscription plan found. Signup aborted."));

      OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
      SubscriptionStatus status = (trialDays == 0) ? SubscriptionStatus.ACTIVE : SubscriptionStatus.TRIAL;

      Subscription subscription = Subscription.builder()
          .plan(plan.getName())
          .status(status)
          .startDate(now)
          .endDate(trialDays > 0 ? now.plusDays(trialDays) : now.plusYears(1))
          .trialEnd(status == SubscriptionStatus.TRIAL ? now.plusDays(trialDays) : null)
          .build();

      try {
        subscriptionRepository.saveAndFlush(subscription);
      } catch (DataIntegrityViolationException e) {
        // FR-04-H: Idempotency safety
        if (e.getMessage() != null && e.getMessage().contains("uk_subscriptions_tenant")) {
          log.warn("Subscription already exists for tenant {}. Skipping.", tenantId);
        } else {
          throw e;
        }
      }

      // 8. Write audit log INSIDE the transaction (PRD §9.2)
      auditLogService.log(
          AuditAction.TENANT_CREATED,
          tenantId,
          TenantContext.PLATFORM_TENANT_ID,
          "Tenant onboarded: " + tenantName
              + " [slug=" + tenant.getWorkspaceSlug()
              + ", code=" + tenant.getCompanyCode()
              + ", plan=" + plan.getName() + "]");

      // 9. Initialize tenant settings (PRD §3.8)
      tenantSettingsService.initializeDefaults(tenantId);

      signupSuccessCounter.increment();
      businessMetrics.incrementTenantOnboarding();
      log.info("Signup: Completed onboarding for tenant id={} name={}",
          tenantId, tenantName);

      return new SignupResponse(
          "Signup successful",
          tenant.getCompanyCode(),
          tenant.getWorkspaceSlug());

    } catch (Exception e) {
      signupFailureCounter.increment();

      // Write SIGNUP_FAILED audit. PRD Phase 2: Use regular log to avoid breaking
      // rollback guarantees
      // with REQUIRES_NEW if anything went wrong with the tenant creation itself.
      try {
        auditLogService.log(
            AuditAction.SIGNUP_FAILED,
            tenantId != null ? tenantId : TenantContext.PLATFORM_TENANT_ID,
            TenantContext.PLATFORM_TENANT_ID,
            "Signup failed for business: " + businessName + ". Error: " + e.getMessage());
      } catch (Exception auditEx) {
        log.error("Failed to log signup failure audit", auditEx);
      }

      log.error("Signup: Failed to initialize tenant id={}. Transaction will roll back.", tenantId, e);

      if (e instanceof DataAccessResourceFailureException) {
        throw new ServiceUnavailableException("Service temporarily unavailable. Please try again.", e);
      }
      throw e;
    } finally {
      if (previousTenant == null) {
        TenantContext.clear();
      } else {
        TenantContext.setTenantId(previousTenant);
      }
    }
  }

  private String resolveUniqueSlug(SignupRequest request) {
    String slug = (request.getWorkspaceSlug() != null && !request.getWorkspaceSlug().isBlank())
        ? normalizeSlug(request.getWorkspaceSlug())
        : generateWorkspaceSlug(request.getBusinessName());

    if (request.getWorkspaceSlug() != null && !request.getWorkspaceSlug().isBlank()) {
      if (tenantRepository.existsByWorkspaceSlug(slug)) {
        throw new ConflictException("Workspace URL already taken", Map.of("field", "workspaceSlug"));
      }
      return slug;
    }

    for (int i = 0; i < MAX_SLUG_RETRIES; i++) {
      if (!tenantRepository.existsByWorkspaceSlug(slug)) {
        return slug;
      }
      duplicateRetryCounter.increment();
      slug = generateWorkspaceSlug(request.getBusinessName());
    }
    throw new ConflictException("Unable to allocate unique workspace slug", Map.of("field", "workspaceSlug"));
  }

  private String resolveUniqueCompanyCode(String businessName) {
    String code = companyCodeGenerator.generateCode(businessName);
    for (int i = 0; i < MAX_CODE_RETRIES; i++) {
      if (!tenantRepository.existsByCompanyCode(code)) {
        return code;
      }
      duplicateRetryCounter.increment();
      code = companyCodeGenerator.generateCode(businessName);
    }
    throw new ConflictException("Unable to allocate unique company code", Map.of("field", "companyCode"));
  }

  private String randomBase36(int len) {
    char[] out = new char[len];
    for (int i = 0; i < len; i++) {
      out[i] = ALPHABET[RNG.nextInt(ALPHABET.length)];
    }
    return new String(out);
  }

  private String normalizeSlug(String input) {
    if (input == null)
      return null;
    String base = input.toLowerCase(Locale.ROOT);
    base = NON_ALNUM.matcher(base).replaceAll("-");
    return TRIM_DASH.matcher(base).replaceAll("");
  }

  private String generateWorkspaceSlug(String businessName) {
    if (businessName == null || businessName.isBlank()) {
      return "tenant-" + randomBase36(6);
    }
    String base = businessName.toLowerCase(Locale.ROOT);
    base = NON_ALNUM.matcher(base).replaceAll("-");
    base = TRIM_DASH.matcher(base).replaceAll("");

    if (base.isEmpty()) {
      base = "tenant";
    }

    if (base.length() > 40) {
      base = base.substring(0, 40);
      base = TRIM_DASH.matcher(base).replaceAll("");
      if (base.isEmpty()) {
        base = "tenant";
      }
    }

    String suffix = randomBase36(6);
    return base + "-" + suffix;
  }
}
