package com.ims.shared.auth;

import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.SignupResponse;
import com.ims.model.Tenant;
import com.ims.model.TenantStatus;
import com.ims.model.User;
import com.ims.model.UserRole;
import com.ims.platform.repository.TenantRepository;
import com.ims.tenant.repository.UserRepository;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
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
  private final com.ims.shared.utils.CompanyCodeGenerator companyCodeGenerator;

  public SignupService(
      TenantRepository tenantRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      TenantInitializationService tenantInitializationService,
      TenantPersistenceService tenantPersistenceService,
      com.ims.shared.utils.CompanyCodeGenerator companyCodeGenerator) {
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.tenantInitializationService = tenantInitializationService;
    this.tenantPersistenceService = tenantPersistenceService;
    this.companyCodeGenerator = companyCodeGenerator;
  }

  @NonNull
  public SignupResponse signup(@NonNull SignupRequest request) {
    TenantContext.clear();
    Objects.requireNonNull(request, "request cannot be null");
    String rawEmail = Objects.requireNonNull(request.getOwnerEmail());
    String normalizedEmail = rawEmail.trim().toLowerCase();

    String businessName = Objects.requireNonNull(request.getBusinessName());
    String workspaceSlug = (request.getWorkspaceSlug() != null && !request.getWorkspaceSlug().isBlank())
        ? Objects.requireNonNull(request.getWorkspaceSlug())
        : generateWorkspaceSlug(businessName);
    workspaceSlug = ensureUniqueWorkspaceSlug(workspaceSlug);

    if (userRepository.findByEmail(normalizedEmail).isPresent()) {
      throw new IllegalArgumentException("Email already registered");
    }

    if (tenantRepository.existsByWorkspaceSlug(workspaceSlug)) {
      throw new IllegalArgumentException("Workspace URL already taken");
    }

    String companyCode = generateUniqueCompanyCode(businessName);

    // 1. Save tenant in its own committed transaction
    Tenant newTenant =
        Objects.requireNonNull(
            Tenant.builder()
                .name(businessName)
                .businessType(request.getBusinessType())
                .workspaceSlug(workspaceSlug)
                .companyCode(companyCode)
                .status(TenantStatus.ACTIVE)
                .plan("FREE")
                .address(request.getAddress())
                .gstin(request.getGstin())
                .build());
 
    Tenant tenant = Objects.requireNonNull(tenantPersistenceService.saveTenant(newTenant));
    log.info(
        "Signup: Created tenant id={} name={}",
        Objects.requireNonNull(tenant.getId()),
        tenant.getName());

    User user =
        Objects.requireNonNull(
            User.builder()
                .name(Objects.requireNonNull(request.getOwnerName()))
                .email(normalizedEmail)
                .phone(request.getOwnerPhone())
                .passwordHash(passwordEncoder.encode(Objects.requireNonNull(request.getPassword())))
                .role(UserRole.ADMIN)
                .scope("TENANT")
                .isActive(true)
                .build());

    Long previousTenant = TenantContext.getTenantId();
    Long tenantId = Objects.requireNonNull(tenant.getId());
    String tenantName = Objects.requireNonNull(tenant.getName());
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

    log.info("Signup: Created owner user and seeded data for tenant={}", tenantId);
    return new SignupResponse(
        "Signup successful",
        Objects.requireNonNull(tenant.getCompanyCode()),
        Objects.requireNonNull(tenant.getWorkspaceSlug()));
  }

  private String generateUniqueCompanyCode(String businessName) {
    String code;
    do {
      code = companyCodeGenerator.generateCode(businessName);
    } while (tenantRepository.existsByCompanyCode(code));
    return code;
  }

  private String generateWorkspaceSlug(String businessName) {
    return businessName.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
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
