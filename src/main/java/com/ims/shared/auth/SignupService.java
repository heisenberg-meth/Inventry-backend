package com.ims.shared.auth;

import com.ims.dto.request.SignupRequest;
import com.ims.model.Tenant;
import com.ims.model.User;
import com.ims.platform.repository.TenantRepository;
import com.ims.tenant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.Objects;
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
  public void signup(SignupRequest request) {
    String normalizedEmail = request.getOwnerEmail().trim().toLowerCase();

    if (request.getWorkspaceSlug() != null
        && !request.getWorkspaceSlug().isBlank()
        && tenantRepository.existsByWorkspaceSlug(request.getWorkspaceSlug())) {
      throw new IllegalArgumentException("Workspace slug already taken");
    }

    if (userRepository.findByEmail(normalizedEmail).isPresent()) {
      throw new IllegalArgumentException("Email already registered");
    }

    // 1. Save tenant in its own committed transaction
    Tenant tenant = Tenant.builder()
        .name(request.getBusinessName())
        .businessType(request.getBusinessType())
        .workspaceSlug(request.getWorkspaceSlug())
        .status("ACTIVE")
        .plan("FREE")
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

      auditLogService.logAudit(
          "SIGNUP",
          "TENANT",
          tenant.getId(),
          "New business registered: " + tenant.getName() + " by " + user.getEmail());
    } finally {
      TenantContext.clear();
    }

    log.info("Signup: Created owner user for tenant={}", tenant.getId());
  }
}
