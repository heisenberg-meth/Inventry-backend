package com.ims.shared.auth;

import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.SignupResponse;
import com.ims.model.Tenant;
import com.ims.shared.utils.CompanyCodeGenerator;
import com.ims.model.TenantStatus;
import com.ims.model.User;
import java.util.Objects;
import com.ims.platform.repository.TenantRepository;
import com.ims.tenant.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SignupService {

  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final TenantInitializationService tenantInitializationService;
  private final TenantPersistenceService tenantPersistenceService;
  private final CompanyCodeGenerator companyCodeGenerator;

  public SignupService(
      TenantRepository tenantRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      TenantInitializationService tenantInitializationService,
      TenantPersistenceService tenantPersistenceService,
      CompanyCodeGenerator companyCodeGenerator) {
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.tenantInitializationService = tenantInitializationService;
    this.tenantPersistenceService = tenantPersistenceService;
    this.companyCodeGenerator = companyCodeGenerator;
  }

  public SignupResponse signup(SignupRequest request) {
    TenantContext.clear();
    String rawEmail = request.getOwnerEmail();
    String normalizedEmail = rawEmail.trim().toLowerCase();

    String businessName = request.getBusinessName();
    String workspaceSlug = (request.getWorkspaceSlug() != null && !request.getWorkspaceSlug().isBlank())
        ? request.getWorkspaceSlug()
        : generateWorkspaceSlug(businessName);
    workspaceSlug = ensureUniqueWorkspaceSlug(workspaceSlug);

    if (userRepository.findByEmailGlobal(normalizedEmail).isPresent()) {
      throw new IllegalArgumentException("Email already registered");
    }

    if (tenantRepository.existsByWorkspaceSlug(workspaceSlug)) {
      throw new IllegalArgumentException("Workspace URL already taken");
    }

    String companyCode = generateUniqueCompanyCode(businessName);

    // 1. Create tenant as PENDING in its own transaction
    Tenant newTenant = Tenant.builder()
        .name(businessName)
        .businessType(request.getBusinessType())
        .workspaceSlug(workspaceSlug)
        .companyCode(companyCode)
        .status(TenantStatus.PENDING)
        .plan("FREE")
        .address(request.getAddress())
        .gstin(request.getGstin())
        .build();

    Tenant tenant;
    try {
      tenant = tenantPersistenceService.saveTenant(newTenant);
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      log.warn("Workspace slug or company code collision for: {}", workspaceSlug);
      throw new IllegalArgumentException("Workspace URL or Company Code already taken, please try again.");
    }
    Long tenantId = tenant.getId();
    String tenantName = tenant.getName();

    try {
      // 2. Create user object (Role is assigned inside initializeTenant)
      User user = User.builder()
          .name(request.getOwnerName())
          .email(Objects.requireNonNull(normalizedEmail))
          .phone(request.getOwnerPhone())
          .passwordHash(Objects.requireNonNull(passwordEncoder.encode(request.getPassword())))
          .scope("TENANT")
          .isActive(true)
          .tenantId(tenantId)
          .build();

      Long previousTenant = TenantContext.getTenantId();
      try {
        TenantContext.setTenantId(tenantId);
        tenantInitializationService.initializeTenant(user, tenantId, tenantName);
      } finally {
        if (previousTenant == null) {
          TenantContext.clear();
        } else {
          TenantContext.setTenantId(previousTenant);
        }
      }

      // 3. Promote to ACTIVE
      tenant.setStatus(TenantStatus.ACTIVE);
      tenantPersistenceService.saveTenant(tenant);

      log.info("Signup: Completed onboarding for tenant id={} name={}", tenantId, tenantName);

      return new SignupResponse(
          "Signup successful",
          tenant.getCompanyCode(),
          tenant.getWorkspaceSlug());

    } catch (Exception e) {
      log.error("Signup: Failed to initialize tenant id={}. Marking as FAILED.", tenantId, e);
      tenant.setStatus(TenantStatus.FAILED);
      tenantPersistenceService.saveTenant(tenant);
      throw e;
    }
  }

  private String generateUniqueCompanyCode(String businessName) {
    String code;
    do {
      code = companyCodeGenerator.generateCode(businessName);
    } while (tenantRepository.existsByCompanyCode(code));
    return Objects.requireNonNull(code);
  }

  private String generateWorkspaceSlug(String businessName) {
    return Objects.requireNonNull(businessName.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", ""));
  }

  private String ensureUniqueWorkspaceSlug(String baseSlug) {
    String slug = baseSlug;
    int counter = 1;
    while (tenantRepository.existsByWorkspaceSlug(slug)) {
      slug = baseSlug + "-" + counter++;
    }
    return slug;
  }
}
