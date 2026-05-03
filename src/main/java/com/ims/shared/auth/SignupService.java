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
import com.ims.shared.utils.CompanyCodeGenerator;
import com.ims.tenant.repository.UserRepository;
import com.ims.tenant.service.TenantSettingsService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class SignupService {

  private static final int MAX_SLUG_RETRIES = 5;
  private static final int MAX_CODE_RETRIES = 5;
  private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]");
  private static final Pattern TRIM_DASH = Pattern.compile("^-+|-+$");
  private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
  private static final Random RNG = new Random();

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

    this.signupSuccessCounter = Counter.builder("signup.success.count").register(meterRegistry);
    this.signupFailureCounter = Counter.builder("signup.failure.count").register(meterRegistry);
    this.duplicateRetryCounter = Counter.builder("signup.duplicate.retry.count").register(meterRegistry);
    this.signupLatencyTimer = Timer.builder("signup.latency").register(meterRegistry);
  }

  public SignupResponse signup(SignupRequest request) {
    return signupLatencyTimer.record(() -> {
      if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
        var existing = tenantRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
          Tenant t = existing.get();
          return new SignupResponse("Signup already completed", t.getCompanyCode(), t.getWorkspaceSlug());
        }
      }

      String normalizedEmail = request.getOwnerEmail().trim().toLowerCase();
      if (userRepository.findByEmailGlobal(normalizedEmail).isPresent()) {
        throw new ConflictException("Email already registered", Map.of("field", "ownerEmail"));
      }

      String workspaceSlug = resolveUniqueSlug(request);
      String companyCode = resolveUniqueCompanyCode(request.getBusinessName());

      Long tenantId = null;
      try {
        TenantContext.setTenantId(TenantContext.PLATFORM_TENANT_ID);
        Tenant tenant;
        try {
          tenant = createTenant(request, workspaceSlug, companyCode);
        } finally {
          TenantContext.clear();
        }

        tenantId = tenant.getId();
        TenantContext.setTenantId(tenantId);
        try {
          return completeOnboarding(tenant, request);
        } finally {
          TenantContext.clear();
        }
      } catch (Exception e) {
        signupFailureCounter.increment();
        log.error("Signup failed for email: {}. Error: {}", normalizedEmail, e.getMessage());

        try {
          auditLogService.log(
              AuditAction.SIGNUP_FAILED,
              tenantId != null ? tenantId : TenantContext.PLATFORM_TENANT_ID,
              TenantContext.PLATFORM_TENANT_ID,
              "Signup failed for business: " + request.getBusinessName() + ". Error: " + e.getMessage());
        } catch (Exception auditEx) {
          log.error("Failed to log signup failure audit", auditEx);
        }

        if (e instanceof RuntimeException re) throw re;
        throw new RuntimeException(e);
      }
    });
  }

  @Transactional
  public Tenant createTenant(SignupRequest request, String workspaceSlug, String companyCode) {
    String normalizedEmail = request.getOwnerEmail().trim().toLowerCase();
    if (userRepository.findByEmailGlobal(normalizedEmail).isPresent()) {
      throw new ConflictException("Email already registered", Map.of("field", "ownerEmail"));
    }

    Tenant tenant = Tenant.builder()
        .name(request.getBusinessName())
        .businessType(request.getBusinessType())
        .workspaceSlug(workspaceSlug)
        .companyCode(companyCode)
        .status(TenantStatus.ACTIVE)
        .plan("TRIAL")
        .address(request.getAddress())
        .gstin(request.getGstin())
        .idempotencyKey(request.getIdempotencyKey())
        .build();

    return tenantRepository.save(tenant);
  }

  @Transactional
  public SignupResponse completeOnboarding(Tenant tenant, SignupRequest request) {
    Long tenantId = tenant.getId();
    String tenantName = tenant.getName();
    String normalizedEmail = request.getOwnerEmail().trim().toLowerCase();

    User user = User.builder()
        .name(request.getOwnerName())
        .email(Objects.requireNonNull(normalizedEmail))
        .phone(request.getOwnerPhone())
        .passwordHash(passwordEncoder.encode(request.getPassword()))
        .scope("TENANT")
        .isActive(true)
        .build();

    tenantInitializationService.initializeTenant(user, tenantId, tenantName);

    int trialDays = systemConfigService.getInt("TRIAL_DAYS", 7);
    SubscriptionPlan plan = subscriptionPlanRepository.findDefaultPlan()
        .orElseThrow(() -> new IllegalStateException("No default subscription plan found"));

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
      if (e.getMessage() != null && e.getMessage().contains("uk_subscriptions_tenant")) {
        log.warn("Subscription already exists for tenant {}. Skipping.", tenantId);
      } else {
        throw e;
      }
    }

    auditLogService.log(
        AuditAction.TENANT_CREATED,
        tenantId,
        TenantContext.PLATFORM_TENANT_ID,
        "Tenant onboarded: " + tenantName + " [slug=" + tenant.getWorkspaceSlug() + "]");

    tenantSettingsService.initializeDefaults(tenantId);

    signupSuccessCounter.increment();
    businessMetrics.incrementTenantOnboarding();
    log.info("Signup: Completed onboarding for tenant id={} name={}", tenantId, tenantName);

    return new SignupResponse("Signup successful", tenant.getCompanyCode(), tenant.getWorkspaceSlug());
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
      if (!tenantRepository.existsByWorkspaceSlug(slug)) return slug;
      duplicateRetryCounter.increment();
      slug = generateWorkspaceSlug(request.getBusinessName());
    }
    throw new ConflictException("Unable to allocate unique workspace slug", Map.of("field", "workspaceSlug"));
  }

  private String resolveUniqueCompanyCode(String businessName) {
    String code = companyCodeGenerator.generateCode(businessName);
    for (int i = 0; i < MAX_CODE_RETRIES; i++) {
      if (!tenantRepository.existsByCompanyCode(code)) return code;
      duplicateRetryCounter.increment();
      code = companyCodeGenerator.generateCode(businessName);
    }
    throw new ConflictException("Unable to allocate unique company code", Map.of("field", "companyCode"));
  }

  private String randomBase36(int len) {
    char[] out = new char[len];
    for (int i = 0; i < len; i++) out[i] = ALPHABET.charAt(RNG.nextInt(ALPHABET.length()));
    return new String(out);
  }

  private String normalizeSlug(String input) {
    if (input == null) return null;
    String base = input.toLowerCase(Locale.ROOT);
    base = NON_ALNUM.matcher(base).replaceAll("-");
    return TRIM_DASH.matcher(base).replaceAll("");
  }

  private String generateWorkspaceSlug(String businessName) {
    if (businessName == null || businessName.isBlank()) return "tenant-" + randomBase36(6);
    String base = businessName.toLowerCase(Locale.ROOT);
    base = NON_ALNUM.matcher(base).replaceAll("-");
    base = TRIM_DASH.matcher(base).replaceAll("");
    if (base.isEmpty()) base = "tenant";
    if (base.length() > 40) {
      base = base.substring(0, 40);
      base = TRIM_DASH.matcher(base).replaceAll("");
      if (base.isEmpty()) base = "tenant";
    }
    return base + "-" + randomBase36(6);
  }
}
