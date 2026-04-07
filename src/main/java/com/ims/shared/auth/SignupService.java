package com.ims.shared.auth;

import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.SignupResponse;
import com.ims.model.Tenant;
import com.ims.model.User;
import com.ims.platform.repository.TenantRepository;
import com.ims.tenant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.Objects;
import com.ims.shared.utils.CompanyCodeGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SignupService {

  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final UserCreationService userCreationService;
  private final TenantPersistenceService tenantPersistenceService;
  private final com.ims.shared.audit.AuditLogService auditLogService;

  // No @Transactional here — each step manages its own transaction
  public SignupResponse signup(SignupRequest request) {
    String normalizedEmail = request.getOwnerEmail().trim().toLowerCase();

    String workspaceSlug = generateWorkspaceSlug(request.getBusinessName());
    workspaceSlug = ensureUniqueWorkspaceSlug(workspaceSlug);

    if (userRepository.findByEmail(normalizedEmail).isPresent()) {
      throw new IllegalArgumentException("Email already registered");
    }

    String companyCode = generateUniqueCompanyCode(request.getBusinessName());

    // 1. Save tenant in its own committed transaction
    Tenant tenant = Tenant.builder()
        .name(request.getBusinessName())
        .businessType(request.getBusinessType())
        .workspaceSlug(workspaceSlug)
        .companyCode(companyCode)
        .status("ACTIVE")
        .plan("FREE")
        .address(request.getAddress())
        .gstin(request.getGstin())
        .build();

    tenant = tenantPersistenceService.saveTenant(Objects.requireNonNull(tenant)); // commits immediately
    log.info("Signup: Created tenant id={} name={}", tenant.getId(), tenant.getName());

    // 2. Now user insert can see the committed tenant
    User user = User.builder()
        .name(request.getOwnerName())
        .email(normalizedEmail)
        .passwordHash(passwordEncoder.encode(request.getPassword()))
        .role("ADMIN")
        .scope("TENANT")
        .tenantId(tenant.getId())
        .isActive(true)
        .build();

    try {
      TenantContext.set(Objects.requireNonNull(tenant.getId()));
      userCreationService.createUserForTenant(Objects.requireNonNull(user), Objects.requireNonNull(tenant.getId()));

      auditLogService.log(
          "SIGNUP",
          tenant.getId(),
          user.getId(),
          "New business registered: " + tenant.getName() + " by " + user.getEmail());
    } finally {
      TenantContext.clear();
    }

    log.info("Signup: Created owner user for tenant={}", tenant.getId());
    return new SignupResponse("Signup successful", tenant.getCompanyCode(), tenant.getWorkspaceSlug());
  }

  private String generateUniqueCompanyCode(String businessName) {
    String code;
    do {
      code = CompanyCodeGenerator.generateCode(businessName);
    } while (tenantRepository.existsByCompanyCode(code));
    return code;
  }

  private String generateWorkspaceSlug(String businessName) {
    return businessName.toLowerCase()
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("(^-|-$)", "");
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
