package com.ims.platform.service;

import com.ims.dto.CreatePlatformUserRequest;
import com.ims.model.Role;
import com.ims.model.User;
import com.ims.model.UserRole;
import com.ims.shared.audit.AuditAction;
import com.ims.shared.audit.AuditLogService;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.repository.RoleRepository;
import com.ims.tenant.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformUserService {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuditLogService auditLogService;

  @Transactional
  public User createPlatformUser(CreatePlatformUserRequest request) {
    if (!request.getRole().equals(UserRole.PLATFORM_ADMIN.name())
        && !request.getRole().equals(UserRole.SUPPORT_ADMIN.name())) {
      throw new IllegalArgumentException("Invalid role. Must be PLATFORM_ADMIN or SUPPORT_ADMIN.");
    }
    if (userRepository.existsByEmailGlobal(request.getEmail())) {
      throw new IllegalArgumentException("Email already in use");
    }

    Role role = roleRepository.findByNameAndTenantIdIsNull(request.getRole())
        .orElseThrow(() -> new EntityNotFoundException("Platform role not found: " + request.getRole()));

    User user = User.builder()
        .name(Objects.requireNonNull(request.getName()))
        .email(Objects.requireNonNull(request.getEmail()))
        .passwordHash(Objects.requireNonNull(passwordEncoder.encode(Objects.requireNonNull(request.getPassword()))))
        .role(Objects.requireNonNull(role))
        .scope("PLATFORM")
        .tenantId(null)
        .isActive(true)
        .build();

    User savedEntity = userRepository.save(Objects.requireNonNull(user));
    User saved = Objects.requireNonNull(savedEntity);
    auditLogService.log(
        AuditAction.CREATE_PLATFORM_ADMIN,
        TenantContext.PLATFORM_TENANT_ID,
        Objects.requireNonNull(saved.getId()),
        "Created platform user: " + saved.getEmail() + " role=" + saved.getRole());
    return saved;
  }

  @Transactional(readOnly = true)
  public Page<User> getPlatformUsers(Pageable pageable) {
    return Objects.requireNonNull(userRepository.findByTenantIdIsNull(pageable));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getPlatformUser(Long id) {
    User user = userRepository
        .findByIdAndTenantIdIsNull(id)
        .orElseThrow(() -> new EntityNotFoundException("Platform user not found"));

    Map<String, Object> response = new HashMap<>();
    response.put("id", user.getId());
    response.put("name", user.getName());
    response.put("email", user.getEmail());
    response.put("role", user.getRole() != null ? user.getRole().getName() : null);
    response.put("status", Boolean.TRUE.equals(user.getIsActive()) ? "ACTIVE" : "SUSPENDED");
    response.put("lastLogin", user.getLastLogin());
    response.put("createdAt", user.getCreatedAt());
    return response;
  }

  @Transactional
  @CacheEvict(value = "permissions", key = "#id", cacheResolver = "tenantAwareCacheResolver")
  public User updatePlatformUserRole(Long id, String role) {
    if (!role.equals(UserRole.PLATFORM_ADMIN.name())
        && !role.equals(UserRole.SUPPORT_ADMIN.name())) {
      throw new IllegalArgumentException("Invalid role. Must be PLATFORM_ADMIN or SUPPORT_ADMIN.");
    }
    User user = userRepository
        .findByIdAndTenantIdIsNull(id)
        .orElseThrow(() -> new EntityNotFoundException("Platform user not found"));

    if (user.hasRole(UserRole.ROOT)) {
      throw new IllegalArgumentException("Cannot modify ROOT user role");
    }

    Role roleEntity = roleRepository.findByNameAndTenantIdIsNull(role)
        .orElseThrow(() -> new EntityNotFoundException("Platform role not found: " + role));
    user.setRole(Objects.requireNonNull(roleEntity));
    User saved = userRepository.save(user);
    return Objects.requireNonNull(saved);
  }

  @Transactional
  public User updatePlatformUser(
      Long id, CreatePlatformUserRequest request) {
    User user = userRepository
        .findByIdAndTenantIdIsNull(id)
        .orElseThrow(() -> new EntityNotFoundException("Platform user not found"));

    if (user.hasRole(UserRole.ROOT)) {
      throw new IllegalArgumentException("Cannot modify ROOT user");
    }

    if (request.getName() != null) {
      user.setName(Objects.requireNonNull(request.getName()));
    }
    if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
      if (userRepository.existsByEmailGlobal(request.getEmail())) {
        throw new IllegalArgumentException("Email already in use");
      }
      user.setEmail(Objects.requireNonNull(request.getEmail()));
    }
    if (request.getRole() != null) {
      if (!request.getRole().equals(UserRole.PLATFORM_ADMIN.name())
          && !request.getRole().equals(UserRole.SUPPORT_ADMIN.name())) {
        throw new IllegalArgumentException(
            "Invalid role. Must be PLATFORM_ADMIN or SUPPORT_ADMIN.");
      }
      Role roleEntity = roleRepository.findByNameAndTenantIdIsNull(request.getRole())
          .orElseThrow(() -> new EntityNotFoundException("Platform role not found: " + request.getRole()));
      user.setRole(Objects.requireNonNull(roleEntity));
    }

    User saved = userRepository.save(user);
    return Objects.requireNonNull(saved);
  }

  @Transactional
  public void suspendPlatformUser(Long id) {
    User user = userRepository
        .findByIdAndTenantIdIsNull(id)
        .orElseThrow(() -> new EntityNotFoundException("Platform user not found"));

    if (user.hasRole(UserRole.ROOT)) {
      throw new IllegalArgumentException("Cannot suspend ROOT user");
    }

    user.setIsActive(false);
    userRepository.save(user);
    auditLogService.log(
        AuditAction.SUSPEND_PLATFORM_ADMIN,
        TenantContext.PLATFORM_TENANT_ID,
        id,
        "Suspended platform user: " + user.getEmail());
    log.info("Platform user suspended: id={}", id);
  }

  @Transactional
  public void activatePlatformUser(Long id) {
    User user = userRepository
        .findByIdAndTenantIdIsNull(id)
        .orElseThrow(() -> new EntityNotFoundException("Platform user not found"));

    user.setIsActive(true);
    userRepository.save(user);
    auditLogService.log(
        AuditAction.ACTIVATE_PLATFORM_ADMIN,
        TenantContext.PLATFORM_TENANT_ID,
        id,
        "Activated platform user: " + user.getEmail());
    log.info("Platform user activated: id={}", id);
  }

  @Transactional
  public void deactivatePlatformUser(Long id) {
    User user = userRepository
        .findByIdAndTenantIdIsNull(id)
        .orElseThrow(() -> new EntityNotFoundException("Platform user not found"));

    if (user.hasRole(UserRole.ROOT)) {
      throw new IllegalArgumentException("Cannot deactivate ROOT user");
    }

    user.setIsActive(false);
    userRepository.save(user);
  }

  @Transactional
  public Map<String, String> resetPlatformUserPassword(
      Long id, String newPassword) {
    User user = userRepository
        .findByIdAndTenantIdIsNull(id)
        .orElseThrow(() -> new EntityNotFoundException("Platform user not found"));

    user.setPasswordHash(Objects.requireNonNull(passwordEncoder.encode(newPassword)));
    user.setResetToken(null);
    user.setResetTokenExpiry(null);
    userRepository.save(user);

    auditLogService.log(
        AuditAction.RESET_PLATFORM_ADMIN_PASSWORD,
        TenantContext.PLATFORM_TENANT_ID,
        id,
        "Reset password for platform user: " + user.getEmail());
    log.info("Password reset for platform user: id={}", id);
    return Objects.requireNonNull(Map.of("message", "Password reset successfully"));
  }
}
