package com.ims.platform.service;

import com.ims.dto.CreatePlatformUserRequest;
import com.ims.model.User;
import com.ims.shared.audit.AuditLogService;
import com.ims.tenant.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformUserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuditLogService auditLogService;

  @Transactional
  public @NonNull User createPlatformUser(@NonNull CreatePlatformUserRequest request) {
    if (!request.getRole().equals("PLATFORM_ADMIN") && !request.getRole().equals("SUPPORT_ADMIN")) {
      throw new IllegalArgumentException("Invalid role. Must be PLATFORM_ADMIN or SUPPORT_ADMIN.");
    }
    if (userRepository.existsByEmail(request.getEmail())) {
      throw new IllegalArgumentException("Email already in use");
    }

    User user =
        User.builder()
            .name(request.getName())
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .role(request.getRole())
            .scope("PLATFORM")
            .tenantId(null)
            .isActive(true)
            .build();

    User saved = Objects.requireNonNull(userRepository.save(Objects.requireNonNull(user)));
    auditLogService.log("CREATE_PLATFORM_ADMIN", null, saved.getId(),
        "Created platform user: " + saved.getEmail() + " role=" + saved.getRole());
    return saved;
  }

  @Transactional(readOnly = true)
  public @NonNull Page<User> getPlatformUsers(@NonNull Pageable pageable) {
    return Objects.requireNonNull(userRepository.findByTenantIdIsNull(pageable));
  }

  @Transactional(readOnly = true)
  public @NonNull Map<String, Object> getPlatformUser(@NonNull Long id) {
    User user =
        userRepository
            .findByIdAndTenantIdIsNull(id)
            .orElseThrow(() -> new EntityNotFoundException("Platform user not found"));

    Map<String, Object> response = new HashMap<>();
    response.put("id", user.getId());
    response.put("name", user.getName());
    response.put("email", user.getEmail());
    response.put("role", user.getRole());
    response.put("status", Boolean.TRUE.equals(user.getIsActive()) ? "ACTIVE" : "SUSPENDED");
    response.put("lastLogin", user.getLastLogin());
    response.put("createdAt", user.getCreatedAt());
    return response;
  }

  @Transactional
  public @NonNull User updatePlatformUserRole(@NonNull Long id, @NonNull String role) {
    if (!role.equals("PLATFORM_ADMIN") && !role.equals("SUPPORT_ADMIN")) {
      throw new IllegalArgumentException("Invalid role. Must be PLATFORM_ADMIN or SUPPORT_ADMIN.");
    }
    User user =
        userRepository
            .findByIdAndTenantIdIsNull(id)
            .orElseThrow(() -> new EntityNotFoundException("Platform user not found"));

    if (user.getRole().equals("ROOT")) {
      throw new IllegalArgumentException("Cannot modify ROOT user role");
    }

    user.setRole(role);
    return Objects.requireNonNull(userRepository.save(user));
  }

  @Transactional
  public void suspendPlatformUser(@NonNull Long id) {
    User user =
        userRepository
            .findByIdAndTenantIdIsNull(id)
            .orElseThrow(() -> new EntityNotFoundException("Platform user not found"));

    if (user.getRole().equals("ROOT")) {
      throw new IllegalArgumentException("Cannot suspend ROOT user");
    }

    user.setIsActive(false);
    userRepository.save(user);
    auditLogService.log("SUSPEND_PLATFORM_ADMIN", null, id,
        "Suspended platform user: " + user.getEmail());
    log.info("Platform user suspended: id={}", id);
  }

  @Transactional
  public void activatePlatformUser(@NonNull Long id) {
    User user =
        userRepository
            .findByIdAndTenantIdIsNull(id)
            .orElseThrow(() -> new EntityNotFoundException("Platform user not found"));

    user.setIsActive(true);
    userRepository.save(user);
    auditLogService.log("ACTIVATE_PLATFORM_ADMIN", null, id,
        "Activated platform user: " + user.getEmail());
    log.info("Platform user activated: id={}", id);
  }

  @Transactional
  public void deactivatePlatformUser(@NonNull Long id) {
    User user =
        userRepository
            .findByIdAndTenantIdIsNull(id)
            .orElseThrow(() -> new EntityNotFoundException("Platform user not found"));

    if (user.getRole().equals("ROOT")) {
      throw new IllegalArgumentException("Cannot deactivate ROOT user");
    }

    user.setIsActive(false);
    userRepository.save(user);
  }

  @Transactional
  public @NonNull Map<String, String> resetPlatformUserPassword(
      @NonNull Long id, @NonNull String newPassword) {
    User user =
        userRepository
            .findByIdAndTenantIdIsNull(id)
            .orElseThrow(() -> new EntityNotFoundException("Platform user not found"));

    user.setPasswordHash(passwordEncoder.encode(newPassword));
    user.setResetToken(null);
    user.setResetTokenExpiry(null);
    userRepository.save(user);

    auditLogService.log("RESET_PLATFORM_ADMIN_PASSWORD", null, id,
        "Reset password for platform user: " + user.getEmail());
    log.info("Password reset for platform user: id={}", id);
    return Objects.requireNonNull(Map.of("message", "Password reset successfully"));
  }
}
