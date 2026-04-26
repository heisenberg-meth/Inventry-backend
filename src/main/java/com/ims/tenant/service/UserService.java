package com.ims.tenant.service;

import com.ims.dto.request.AssignPermissionsRequest;
import com.ims.dto.request.CreateUserRequest;
import com.ims.dto.response.UserResponse;
import com.ims.model.Permission;
import com.ims.model.Role;
import com.ims.model.User;
import com.ims.model.UserRole;
import com.ims.platform.repository.TenantRepository;
import com.ims.shared.audit.AuditAction;
import com.ims.shared.audit.AuditLogService;
import com.ims.shared.audit.AuditResource;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.repository.PermissionRepository;
import com.ims.tenant.repository.RoleRepository;
import com.ims.tenant.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

  private final UserRepository userRepository;
  private final TenantRepository tenantRepository;
  private final PasswordEncoder passwordEncoder;
  private final PermissionRepository permissionRepository;
  private final RoleRepository roleRepository;
  private final AuditLogService auditLogService;

  private static final List<String> VALID_TENANT_ROLES = List.of("ADMIN", "MANAGER", "STAFF");

  public @NonNull Page<UserResponse> getUsers(@NonNull Pageable pageable) {
    return userRepository
        .findAll(Objects.requireNonNull(pageable))
        .map(user -> toResponse(Objects.requireNonNull(user), false));
  }

  public @NonNull UserResponse getUserById(@NonNull Long id) {
    User tmpUser =
        userRepository
            .findByIdWithPermissions(Objects.requireNonNull(id))
            .orElseThrow(() -> new EntityNotFoundException("User not found"));
    User user = Objects.requireNonNull(tmpUser);
    return toResponse(user, true);
  }

  @Transactional
  public @NonNull UserResponse createUser(@NonNull CreateUserRequest request) {
    // Validate role is tenant-level only
    if (!VALID_TENANT_ROLES.contains(request.getRole())) {
      throw new IllegalArgumentException("Invalid role. Must be one of: " + VALID_TENANT_ROLES);
    }

    // Email must be unique platform-wide
    if (userRepository.findByEmailUnfiltered(request.getEmail()).isPresent()) {
      throw new IllegalArgumentException("Email already in use: " + request.getEmail());
    }

    // Check user limits for tenant
    TenantContext.assertTenantPresent();
    Long tenantId = TenantContext.getTenantId();

    var tmpTenant =
        tenantRepository
            .lockById(tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
    var tenant = Objects.requireNonNull(tmpTenant);

    if (tenant.getMaxUsers() != null) {
      long currentCount = userRepository.countActiveByTenantId(tenantId);
      if (currentCount >= tenant.getMaxUsers()) {
        throw new IllegalArgumentException(
            "User limit reached for your plan (" + tenant.getMaxUsers() + ")");
      }
    }

    User tmpUser =
        User.builder()
            .name(request.getName())
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .role(UserRole.valueOf(request.getRole()))
            .scope("TENANT")
            .isActive(true)
            .build();
    User user = Objects.requireNonNull(tmpUser);

    User saved = userRepository.save(Objects.requireNonNull(user));
    user = Objects.requireNonNull(saved);

    auditLogService.logAudit(
        AuditAction.CREATE,
        AuditResource.USER,
        Objects.requireNonNull(user.getId()),
        "Created user: " + user.getEmail() + " with role: " + user.getRole());

    log.info("User created: id={} email={} role={}", user.getId(), user.getEmail(), user.getRole());
    return toResponse(user, true);
  }

  @Transactional
  @CacheEvict(value = "permissions", key = "#id", cacheResolver = "tenantAwareCacheResolver")
  public @NonNull UserResponse updateRole(@NonNull Long id, @NonNull String newRole) {
    if (!VALID_TENANT_ROLES.contains(newRole)) {
      throw new IllegalArgumentException("Invalid role. Must be one of: " + VALID_TENANT_ROLES);
    }

    User user =
        Objects.requireNonNull(
            userRepository
                .findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new EntityNotFoundException("User not found")));

    user.setRole(UserRole.valueOf(newRole));
    User saved = userRepository.save(Objects.requireNonNull(user));
    user = Objects.requireNonNull(saved);

    auditLogService.logAudit(
        AuditAction.UPDATE_ROLE,
        AuditResource.USER,
        id,
        "Updated role for user " + user.getEmail() + " to " + newRole);

    log.info("User role updated: id={} newRole={}", id, newRole);
    return toResponse(user, true);
  }

  @Transactional
  @CacheEvict(value = "permissions", key = "#id", cacheResolver = "tenantAwareCacheResolver")
  public @NonNull UserResponse assignPermissions(
      @NonNull Long id, @NonNull AssignPermissionsRequest request) {
    Objects.requireNonNull(id, "user id required");
    Objects.requireNonNull(request, "request body required");
    User tmpUser =
        userRepository
            .findByIdWithPermissions(id)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));
    User user = Objects.requireNonNull(tmpUser);

    var perms = permissionRepository.findByIdIn(request.getPermissionIds());
    user.setCustomPermissions(new HashSet<>(perms));
    userRepository.save(user);

    auditLogService.logAudit(
        AuditAction.ASSIGN_PERMISSIONS,
        AuditResource.USER,
        id,
        "Assigned " + perms.size() + " custom permissions to user: " + user.getEmail());

    return toResponse(user, true);
  }

  @Transactional
  public void deactivateUser(@NonNull Long id) {
    User user =
        Objects.requireNonNull(
            userRepository
                .findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new EntityNotFoundException("User not found")));
    user.setIsActive(false);
    userRepository.save(Objects.requireNonNull(user));

    auditLogService.logAudit(
        AuditAction.DEACTIVATE,
        AuditResource.USER,
        Objects.requireNonNull(id),
        "Deactivated user account: " + user.getEmail());
    log.info("User deactivated: id={}", id);
  }

  private @Nullable Long getTenantId() {
    return TenantContext.getTenantId();
  }

  private @NonNull UserResponse toResponse(@NonNull User user, boolean includePermissions) {
    List<String> permissions = null;

    if (includePermissions) {
      Set<String> allPermissions = new HashSet<>();

      // 1. Role permissions
      if (user.getRole() != null) {
        Long tenantId = getTenantId();
        Optional<Role> roleOpt =
            tenantId != null
                ? roleRepository.findByNameAndTenantIdWithPermissions(
                    user.getRole().name(), tenantId)
                : roleRepository.findByNameAndTenantIdIsNullWithPermissions(user.getRole().name());

        roleOpt.ifPresent(
            role ->
                allPermissions.addAll(
                    role.getPermissions().stream()
                        .map(Permission::getKey)
                        .collect(Collectors.toSet())));
      }

      // 2. Custom permissions
      allPermissions.addAll(
          user.getCustomPermissions().stream().map(Permission::getKey).collect(Collectors.toSet()));

      permissions = new java.util.ArrayList<>(allPermissions);
    }

    return Objects.requireNonNull(
        UserResponse.builder()
            .id(user.getId())
            .name(user.getName())
            .email(user.getEmail())
            .role(user.getRole() != null ? user.getRole().name() : null)
            .scope(user.getScope())
            .isActive(user.getIsActive())
            .permissions(permissions)
            .createdAt(user.getCreatedAt())
            .build());
  }
}
