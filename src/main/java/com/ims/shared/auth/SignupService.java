package com.ims.shared.auth;
 
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.SignupResponse;
import com.ims.model.Tenant;
import com.ims.model.TenantStatus;
import com.ims.model.User;
import com.ims.model.UserRole;
import com.ims.platform.repository.TenantRepository;
import com.ims.tenant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
 
@Service
@RequiredArgsConstructor
@Slf4j
public class SignupService {
 
  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final TenantInitializationService tenantInitializationService;
  private final TenantPersistenceService tenantPersistenceService;
  private final com.ims.shared.utils.CompanyCodeGenerator companyCodeGenerator;
 
  public SignupResponse signup(SignupRequest request) {
    String normalizedEmail = request.getOwnerEmail().trim().toLowerCase();
 
    String workspaceSlug = (request.getWorkspaceSlug() != null && !request.getWorkspaceSlug().isBlank())
        ? request.getWorkspaceSlug()
        : generateWorkspaceSlug(request.getBusinessName());
    workspaceSlug = ensureUniqueWorkspaceSlug(workspaceSlug);
 
    if (userRepository.findByEmail(normalizedEmail).isPresent()) {
      throw new IllegalArgumentException("Email already registered");
    }
 
    if (tenantRepository.existsByWorkspaceSlug(workspaceSlug)) {
        throw new IllegalArgumentException("Workspace URL already taken");
    }
 
    String companyCode = generateUniqueCompanyCode(request.getBusinessName());
 
    // 1. Save tenant in its own committed transaction
    Tenant tenant = Tenant.builder()
        .name(request.getBusinessName())
        .businessType(request.getBusinessType())
        .workspaceSlug(workspaceSlug)
        .companyCode(companyCode)
        .status(TenantStatus.ACTIVE)
        .plan("FREE")
        .address(request.getAddress())
        .gstin(request.getGstin())
        .build();
 
    tenant = tenantPersistenceService.saveTenant(tenant); // commits immediately
    log.info("Signup: Created tenant id={} name={}", tenant.getId(), tenant.getName());
 
    // 2. Now user and data initialization in its own transaction (correctly bound to new tenant)
    User user = User.builder()
        .name(request.getOwnerName())
        .email(normalizedEmail)
        .phone(request.getOwnerPhone())
        .passwordHash(passwordEncoder.encode(request.getPassword()))
        .role(UserRole.ADMIN)
        .scope("TENANT")
        .isActive(true)
        .build();
 
    tenantInitializationService.initializeTenant(user, tenant.getId(), tenant.getName());
 
    log.info("Signup: Created owner user and seeded data for tenant={}", tenant.getId());
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
