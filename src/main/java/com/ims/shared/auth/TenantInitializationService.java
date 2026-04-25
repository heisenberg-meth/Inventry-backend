package com.ims.shared.auth;

import com.ims.category.CategoryService;
import com.ims.dto.CategoryRequest;
import com.ims.model.User;
import com.ims.shared.audit.AuditAction;
import com.ims.shared.audit.AuditLogService;
import com.ims.shared.email.EmailService;
import com.ims.tenant.repository.UserRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
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
  private final CategoryService categoryService;
  private final EmailService emailService;
  private final AuditLogService auditLogService;
  private final PasswordEncoder passwordEncoder;

  @Transactional(propagation = Propagation.REQUIRED)
  public User initializeTenant(
      @NonNull User user, @NonNull Long tenantId, @NonNull String tenantName) {
    Long oldTenantId = TenantContext.getTenantId();
    try {
      TenantContext.setTenantId(Objects.requireNonNull(tenantId));

      // 1. Seed default category
      CategoryRequest catReq = new CategoryRequest();
      catReq.setName("General");
      catReq.setDescription("Default category");
      categoryService.create(catReq);

      // 2. Generate and hash email verification token
      String rawToken = java.util.UUID.randomUUID().toString();
      String hashedToken = passwordEncoder.encode(rawToken);

      // Create the owner user with the verification token already applied.
      User savedUser = userRepository.save(user);
      savedUser.setVerificationToken(hashedToken);
      savedUser.setVerificationTokenExpiry(
          java.time.LocalDateTime.now().plusMinutes(VERIFICATION_TOKEN_EXPIRY_MINUTES));
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
                  finalUserId,
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
  public User createUserForTenant(@NonNull User user, @NonNull Long tenantId) {
    Long oldTenantId = TenantContext.getTenantId();
    try {
      TenantContext.setTenantId(Objects.requireNonNull(tenantId));
      return userRepository.save(user);
    } finally {
      if (oldTenantId == null) {
        TenantContext.clear();
      } else {
        TenantContext.setTenantId(oldTenantId);
      }
    }
  }
}
