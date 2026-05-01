package com.ims.shared.auth;

import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.SignupResponse;
import com.ims.model.Subscription;
import com.ims.model.SubscriptionStatus;
import com.ims.model.Tenant;
import com.ims.model.TenantStatus;
import com.ims.model.User;
import com.ims.platform.repository.SubscriptionRepository;
import com.ims.platform.repository.TenantRepository;
import com.ims.shared.audit.AuditAction;
import com.ims.shared.audit.AuditLogService;
import com.ims.shared.exception.CodeGenerationException;
import com.ims.shared.exception.ConflictException;
import com.ims.shared.exception.ServiceUnavailableException;
import com.ims.shared.utils.CompanyCodeGenerator;
import com.ims.tenant.repository.UserRepository;
import com.ims.tenant.service.TenantSettingsService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class SignupService {

  private static final int MAX_SLUG_RETRIES = 5;
  private static final int MAX_CODE_RETRIES = 5;

  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final TenantInitializationService tenantInitializationService;
  private final CompanyCodeGenerator companyCodeGenerator;
  private final SubscriptionRepository subscriptionRepository;
  private final AuditLogService auditLogService;
  private final TenantSettingsService tenantSettingsService;

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
      MeterRegistry meterRegistry) {
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.tenantInitializationService = tenantInitializationService;
    this.companyCodeGenerator = companyCodeGenerator;
    this.subscriptionRepository = subscriptionRepository;
    this.auditLogService = auditLogService;
    this.tenantSettingsService = tenantSettingsService;

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

  @Transactional
  public SignupResponse signup(SignupRequest request) {
    return signupLatencyTimer.record(() -> doSignup(request));
  }

  private SignupResponse doSignup(SignupRequest request) {
    // 0. Idempotency Check (PRD §4.3)
    if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
      var existing = tenantRepository.findByIdempotencyKey(request.getIdempotencyKey());
      if (existing.isPresent()) {
        log.info("Signup: Returning existing tenant for idempotency key: {}",
            request.getIdempotencyKey());
        Tenant t = existing.get();
        return new SignupResponse(
            "Signup already completed",
            t.getCompanyCode(),
            t.getWorkspaceSlug());
      }
    }

    TenantContext.clear();
    String normalizedEmail = request.getOwnerEmail().trim().toLowerCase();
    String businessName = request.getBusinessName();

    // 1. Validate email uniqueness — advisory check; DB constraint is the real
    // guard (PRD §3.1, §5.1)
    if (userRepository.findByEmailGlobal(normalizedEmail).isPresent()) {
      throw new ConflictException("Email already registered");
    }

    // 2. Generate workspace slug
    String workspaceSlug = (request.getWorkspaceSlug() != null
        && !request.getWorkspaceSlug().isBlank())
            ? request.getWorkspaceSlug()
            : generateWorkspaceSlug(businessName);

    // 3. Generate company code
    String companyCode = companyCodeGenerator.generateCode(businessName);

    // 4. INSERT-FIRST with retry on constraint violation (PRD §5.2, §6.1, §6.2)
    // This eliminates the check-then-insert race condition anti-pattern.
    Tenant tenant = insertTenantWithRetry(
        businessName, request.getBusinessType(), workspaceSlug, companyCode,
        request.getAddress(), request.getGstin(), request.getIdempotencyKey());
    Long tenantId = tenant.getId();
    String tenantName = tenant.getName();

    Long previousTenant = TenantContext.getTenantId();
    try {
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
          .tenantId(tenantId)
          .build();

      // 6. Initialize tenant: seeds roles, creates user, sends email (PRD §4.1 steps
      // 4-5, §7)
      tenantInitializationService.initializeTenant(user, tenantId, tenantName);

      // 7. Create default subscription (PRD §3.2 Step 4)
      Subscription subscription = Subscription.builder()
          .tenantId(tenantId)
          .plan("FREE")
          .status(SubscriptionStatus.TRIAL)
          .startDate(LocalDateTime.now())
          .endDate(LocalDateTime.now().plusDays(7))
          .build();
      subscriptionRepository.save(subscription);

      // 8. Write audit log INSIDE the transaction (PRD §9.2)
      auditLogService.log(
          AuditAction.TENANT_CREATED,
          tenantId,
          TenantContext.PLATFORM_TENANT_ID,
          "Tenant onboarded: " + tenantName
              + " [slug=" + tenant.getWorkspaceSlug()
              + ", code=" + tenant.getCompanyCode() + "]");

      // 9. Initialize tenant settings (PRD §3.8)
      tenantSettingsService.initializeDefaults(tenantId);

      signupSuccessCounter.increment();
      log.info("Signup: Completed onboarding for tenant id={} name={}",
          tenantId, tenantName);

      return new SignupResponse(
          "Signup successful",
          tenant.getCompanyCode(),
          tenant.getWorkspaceSlug());

    } catch (Exception e) {
      signupFailureCounter.increment();

      // Write SIGNUP_FAILED audit in a new transaction so it persists (PRD §2.1 G6)
      auditLogService.logRequiresNew(
          AuditAction.SIGNUP_FAILED,
          tenantId != null ? tenantId : TenantContext.PLATFORM_TENANT_ID,
          TenantContext.PLATFORM_TENANT_ID,
          "Signup failed for business: " + businessName + ". Error: " + e.getMessage());

      log.error("Signup: Failed to initialize tenant id={}. "
          + "Transaction will roll back.", tenantId, e);

      // Map infrastructure failures to 503 (PRD §3.11)
      if (e instanceof DataAccessResourceFailureException) {
        throw new ServiceUnavailableException(
            "Service temporarily unavailable. Please try again.", e);
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

  /**
   * INSERT-FIRST strategy (PRD §5.2, §6.1, §6.2).
   * Attempts to insert the tenant. On slug or company code collision,
   * regenerates and retries up to MAX retries.
   */
  private Tenant insertTenantWithRetry(
      String businessName, String businessType, String baseSlug,
      String baseCode, String address, String gstin, String idempotencyKey) {

    String slug = baseSlug;
    String code = baseCode;
    int slugSuffix = 1;

    for (int attempt = 0; attempt < MAX_SLUG_RETRIES + MAX_CODE_RETRIES; attempt++) {
      Tenant newTenant = Tenant.builder()
          .name(businessName)
          .businessType(businessType)
          .workspaceSlug(slug)
          .companyCode(code)
          .status(TenantStatus.ACTIVE) // NG3: Self-service, no pending state
          .plan("TRIAL") // NG2: Trial plan instead of FREE
          .address(address)
          .gstin(gstin)
          .idempotencyKey(idempotencyKey)
          .build();

      try {
        // PRD §3.2: Explicit saveAndFlush to guarantee tenant exists before FK usage
        Tenant saved = tenantRepository.saveAndFlush(newTenant);
        return saved;
      } catch (DataIntegrityViolationException e) {
        duplicateRetryCounter.increment();
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

        if (msg.contains("workspace_slug") || msg.contains("idx_tenants_workspace_slug")) {
          slug = baseSlug + "-" + slugSuffix++;
          log.warn("Slug collision, retrying with: {}", slug);
        } else if (msg.contains("company_code") || msg.contains("uk_tenants_company_code")) {
          code = companyCodeGenerator.generateCode(businessName);
          log.warn("Company code collision, retrying with: {}", code);
        } else if (msg.contains("idempotency_key")) {
          // Race: another thread completed with the same idempotency key
          var existing = tenantRepository.findByIdempotencyKey(idempotencyKey);
          if (existing.isPresent()) {
            return existing.get();
          }
          throw new ConflictException(
              "Duplicate signup detected. Please retry.", e);
        } else {
          throw new ConflictException(
              "Workspace URL or Company Code already taken, please try again.", e);
        }
      }
    }

    throw new CodeGenerationException("Unable to generate unique company code");
  }

  private String generateWorkspaceSlug(String businessName) {
    return Objects.requireNonNull(
        businessName.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", ""));
  }
}
