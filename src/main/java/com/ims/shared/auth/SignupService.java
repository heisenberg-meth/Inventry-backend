package com.ims.shared.auth;

import com.ims.dto.request.SignupRequest;
import com.ims.model.Tenant;
import com.ims.model.User;
import com.ims.platform.repository.TenantRepository;
import com.ims.tenant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SignupService {

  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final UserCreationService userCreationService;

  @Transactional
  public void signup(SignupRequest request) {
    if (request.getDomain() != null
        && !request.getDomain().isBlank()
        && tenantRepository.existsByDomain(request.getDomain())) {
      throw new IllegalArgumentException("Domain already taken: " + request.getDomain());
    }

    if (userRepository.findByEmail(request.getOwnerEmail()).isPresent()) {
      throw new IllegalArgumentException("Email already registered: " + request.getOwnerEmail());
    }

    // 1. Create Tenant (implicitly in tenant 0 context)
    Tenant tenant =
        Tenant.builder()
            .name(request.getBusinessName())
            .businessType(request.getBusinessType())
            .domain(request.getDomain())
            .status("ACTIVE")
            .plan("FREE")
            .build();

    tenant = tenantRepository.saveAndFlush(tenant);
    log.info("Signup: Created tenant id={} name={}", tenant.getId(), tenant.getName());

    // 2. Create Owner User (ADMIN) - Must match tenant context
    User user =
        User.builder()
            .name(request.getOwnerName())
            .email(request.getOwnerEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .role("ADMIN")
            .tenantId(tenant.getId())
            .isActive(true)
            .build();

    try {
      TenantContext.set(tenant.getId());
      userCreationService.createUserForTenant(user, tenant.getId());
    } finally {
      TenantContext.clear();
    }
    
    log.info("Signup: Created owner user for tenant={}", tenant.getId());
  }
}
