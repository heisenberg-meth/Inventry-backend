package com.ims.shared.auth;

import com.ims.category.CategoryService;
import com.ims.dto.CategoryRequest;
import com.ims.model.Role;
import com.ims.model.User;
import com.ims.model.UserRole;
import com.ims.shared.audit.AuditAction;
import com.ims.shared.audit.AuditLogService;
import com.ims.shared.email.EmailService;
import com.ims.tenant.repository.RoleRepository;
import com.ims.tenant.repository.UserRepository;
import java.util.Objects;
import java.util.UUID;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantInitializationService {

  private static final int VERIFICATION_TOKEN_EXPIRY_MINUTES = 15;

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final CategoryService categoryService;
  private final EmailService emailService;
  private final AuditLogService auditLogService;
  private final PasswordEncoder passwordEncoder;

  @Transactional(propagation = Propagation.REQUIRED)
  public User initializeTenant(
      User user, Long tenantId, String tenantName) {
    Long oldTenantId = TenantContext.getTenantId();
    try {
      TenantContext.setTenantId(tenantId);

      // 1. Seed default roles
      seedDefaultRoles(tenantId);

      // 2. Resolve ADMIN role for the owner
      Role adminRole = roleRepository.findByNameAndTenantId(UserRole.ADMIN.name(), tenantId)
          .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded for tenant"));
      user.setRole(Objects.requireNonNull(adminRole));

      // 3. Seed default category
      CategoryRequest catReq = new CategoryRequest();
      catReq.setName("General");
      catReq.setDescription("Default category");
      categoryService.create(catReq);

      // 4. Generate and hash email verification token
      String rawToken = UUID.randomUUID().toString();
      String hashedToken = passwordEncoder.encode(rawToken);

      // Create the owner user with the verification token already applied.
      User savedUser = userRepository.save(user);
      savedUser.setVerificationToken(hashedToken);
      savedUser.setVerificationTokenExpiry(
          LocalDateTime.now().plusMinutes(VERIFICATION_TOKEN_EXPIRY_MINUTES));
      savedUser = userRepository.save(savedUser);

      final String finalEmail = savedUser.getEmail();
      final String finalRawToken = rawToken;
      final Long finalTenantId = tenantId;
      final Long finalUserId = savedUser.getId();
      final String finalTenantName = tenantName;

      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              emailService.sendVerificationEmail(finalEmail, finalRawToken);
              auditLogService.log(
                  AuditAction.SIGNUP,
                  finalTenantId,
                  Objects.requireNonNull(finalUserId),
                  "New business registered: " + finalTenantName + " by " + finalEmail);
            }
          });

      return savedUser;
    } finally {
      if (oldTenantId == null) {
        TenantContext.clear();
      } else {
        TenantContext.setTenantId(oldTenantId);
      }
    }
  }

  @Transactional(propagation = Propagation.REQUIRED)
  public User createUserForTenant(User user, Long tenantId) {
    Long oldTenantId = TenantContext.getTenantId();
    try {
      TenantContext.setTenantId(tenantId);
      return userRepository.save(user);
    } finally {
      if (oldTenantId == null) {
        TenantContext.clear();
      } else {
        TenantContext.setTenantId(oldTenantId);
      }
    }
  }

  private void seedDefaultRoles(Long tenantId) {
    for (UserRole roleName : new UserRole[]{UserRole.ADMIN, UserRole.MANAGER, UserRole.STAFF}) {
      if (roleRepository.findByNameAndTenantId(roleName.name(), tenantId).isEmpty()) {
        Role role = Role.builder()
            .name(Objects.requireNonNull(roleName.name()))
            .description("Default " + roleName.name() + " role")
            .tenantId(tenantId)
            .build();
        roleRepository.save(role);
      }
    }
    log.info("Default roles seeded for tenant: {}", tenantId);
  }
}
