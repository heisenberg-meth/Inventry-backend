package com.ims.shared.auth;

import com.ims.shared.audit.AuditAction;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.SignupResponse;
import com.ims.model.Tenant;
import com.ims.model.User;
import com.ims.model.EmailVerification;
import com.ims.platform.repository.TenantRepository;
import com.ims.tenant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class SignupService {

  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final UserCreationService userCreationService;
  private final TenantPersistenceService tenantPersistenceService;
  private final com.ims.category.CategoryService categoryService;
  private final EmailVerificationRepository emailVerificationRepository;
  private final com.ims.shared.audit.AuditLogService auditLogService;
  private final com.ims.shared.utils.CompanyCodeGenerator companyCodeGenerator;

  // No @Transactional here — each step manages its own transaction
  public SignupResponse signup(SignupRequest request) {
    String normalizedEmail = request.getOwnerEmail().trim().toLowerCase();

    String workspaceSlug = (request.getWorkspaceSlug() != null && !request.getWorkspaceSlug().isBlank())
        ? request.getWorkspaceSlug()
        : generateWorkspaceSlug(request.getBusinessName());
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
        .status("ACTIVE")
        .plan("FREE")
        .address(request.getAddress())
        .gstin(request.getGstin())
        .build();

    tenant = tenantPersistenceService.saveTenant(tenant); // commits immediately
    log.info("Signup: Created tenant id={} name={}", tenant.getId(), tenant.getName());

    // 2. Now user insert can see the committed tenant
    User user = User.builder()
        .name(request.getOwnerName())
        .email(normalizedEmail)
        .phone(request.getOwnerPhone())
        .passwordHash(passwordEncoder.encode(request.getPassword()))
        .role("ADMIN")
        .scope("TENANT")
        .tenantId(tenant.getId())
        .isActive(true)
        .build();

    try {
      TenantContext.setTenantId(tenant.getId());
      user = userCreationService.createUserForTenant(user, tenant.getId());

      // Seed default category
      com.ims.dto.CategoryRequest catReq = new com.ims.dto.CategoryRequest();
      catReq.setName("General");
      catReq.setDescription("Default category");
      categoryService.create(catReq);

      // Generate email verification token
      String verificationToken = java.util.UUID.randomUUID().toString();
      EmailVerification verification = EmailVerification.builder()
          .userId(user.getId())
          .token(verificationToken)
          .expiresAt(java.time.LocalDateTime.now().plusHours(24))
          .build();
      emailVerificationRepository.save(verification);
      log.info("Signup: Created email verification token for user={}", user.getId());

      auditLogService.log(
          AuditAction.SIGNUP,
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
      code = companyCodeGenerator.generateCode(businessName);
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
