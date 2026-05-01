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
import com.ims.tenant.repository.UserSummaryView;
import jakarta.persistence.EntityNotFoundException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
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
public class UserService {

  private final UserRepository userRepository;
  private final TenantRepository tenantRepository;
  private final PasswordEncoder passwordEncoder;
  private final PermissionRepository permissionRepository;
  private final RoleRepository roleRepository;
  private final AuditLogService auditLogService;

  private static final List<String> VALID_TENANT_ROLES = List.of(
      UserRole.TENANT_ADMIN.name(),
      UserRole.BUSINESS_MANAGER.name(),
      UserRole.SALES_STAFF.name());

  /**
   * Fetches user summaries using an optimized projection.
   * This avoids N+1 queries by joining roles at the database level.
   */
  public Page<UserResponse> getUsers(Pageable pageable) {
    TenantContext.assertTenantPresent();
    return Objects.requireNonNull(
        userRepository
            .findSummaries(Objects.requireNonNull(pageable))
            .map(this::toResponseFromView));
  }

  /**
   * Fetches a single user with all role and permission details in a single query.
   */
  public UserResponse getUserById(Long id) {
    TenantContext.assertTenantPresent();
    User tmpUser = userRepository
        .findByIdWithFullDetails(Objects.requireNonNull(id))
        .orElseThrow(() -> new EntityNotFoundException("User not found"));
    User user = Objects.requireNonNull(tmpUser);
    return toResponse(user, true);
  }

  @Transactional
  public UserResponse createUser(CreateUserRequest request) {
    // Validate role is tenant-level only
    if (!VALID_TENANT_ROLES.contains(request.getRole())) {
      throw new IllegalArgumentException("Invalid role. Must be one of: " + VALID_TENANT_ROLES);
    }

    // Email must be unique platform-wide
    if (userRepository.findByEmailGlobal(request.getEmail()).isPresent()) {
      throw new IllegalArgumentException("Email already in use: " + request.getEmail());
    }

    // Check user limits for tenant
    TenantContext.assertTenantPresent();
    Long tenantId = TenantContext.getTenantId();

    var tmpTenant = tenantRepository
        .lockById(tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
    var tenant = Objects.requireNonNull(tmpTenant);

    if (tenant.getMaxUsers() != null) {
      long currentCount = userRepository.countActive();
      if (currentCount >= tenant.getMaxUsers()) {
        throw new IllegalArgumentException(
            "User limit reached for your plan (" + tenant.getMaxUsers() + ")");
      }
    }

    User user = User.builder()
        .tenantId(tenantId)
        .name(request.getName())
        .email(request.getEmail())
        .passwordHash(Objects.requireNonNull(passwordEncoder.encode(request.getPassword())))
        .role(
            Objects.requireNonNull(
                roleRepository
                    .findByName(request.getRole())
                    .orElseThrow(() -> new EntityNotFoundException("Role not found: " + request.getRole()))))
        .scope("TENANT")
        .isActive(true)
        .build();

    User saved = userRepository.save(user);

    auditLogService.logAudit(
        AuditAction.CREATE,
        AuditResource.USER,
        Objects.requireNonNull(saved.getId()),
        "Created user: " + saved.getEmail() + " with role: " + saved.getRole().getName());

    log.info("User created: id={} email={} role={}", saved.getId(), saved.getEmail(), saved.getRole().getName());
    return toResponse(saved, true);
  }

  @Transactional
  @CacheEvict(value = "permissions", key = "#id", cacheResolver = "tenantAwareCacheResolver")
  public UserResponse updateRole(Long id, String newRole) {
    if (!VALID_TENANT_ROLES.contains(newRole)) {
      throw new IllegalArgumentException("Invalid role. Must be one of: " + VALID_TENANT_ROLES);
    }

    Long tenantId = TenantContext.requireTenantId();
    User user = Objects.requireNonNull(
        userRepository
            .findById(Objects.requireNonNull(id))
            .filter(u -> tenantId.equals(u.getTenantId()))
            .orElseThrow(() -> new EntityNotFoundException("User not found")));

    Role role = Objects.requireNonNull(
        roleRepository
            .findByName(newRole)
            .orElseThrow(() -> new EntityNotFoundException("Role not found: " + newRole)));

    user.setRole(role);
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
  public UserResponse assignPermissions(
      Long id, AssignPermissionsRequest request) {
    Objects.requireNonNull(id, "user id required");
    Objects.requireNonNull(request, "request body required");
    User tmpUser = userRepository
        .findByIdWithFullDetails(id)
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
  public void deactivateUser(Long id) {
    Long tenantId = TenantContext.requireTenantId();
    User user = Objects.requireNonNull(
        userRepository
            .findById(Objects.requireNonNull(id))
            .filter(u -> tenantId.equals(u.getTenantId()))
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

  /**
   * Optimized mapping from projection to response.
   * No database queries performed here.
   */
  private UserResponse toResponseFromView(UserSummaryView view) {
    return Objects.requireNonNull(
        UserResponse.builder()
            .id(view.getId())
            .name(view.getName())
            .email(view.getEmail())
            .role(view.getRoleName())
            .scope(view.getScope())
            .isActive(view.getIsActive())
            .createdAt(view.getCreatedAt())
            .build());
  }

  /**
   * Mapping from entity to response.
   * Optimized to use already loaded relationships (Role, Permissions) from JOIN
   * FETCH.
   */
  private UserResponse toResponse(User user, boolean includePermissions) {
    List<String> permissions = null;

    if (includePermissions) {
      Set<String> allPermissions = new HashSet<>();

      // 1. Role permissions (Loaded via JOIN FETCH in repository)
      if (user.getRole() != null) {
        allPermissions.addAll(
            user.getRole().getPermissions().stream()
                .map(Permission::getKey)
                .collect(Collectors.toSet()));
      }

      // 2. Custom permissions (Loaded via JOIN FETCH in repository)
      allPermissions.addAll(
          user.getCustomPermissions().stream().map(Permission::getKey).collect(Collectors.toSet()));

      permissions = new ArrayList<>(allPermissions);
    }

    return Objects.requireNonNull(
        UserResponse.builder()
            .id(user.getId())
            .name(user.getName())
            .email(user.getEmail())
            .role(user.getRole() != null ? user.getRole().getName() : null)
            .scope(user.getScope())
            .isActive(user.getIsActive())
            .permissions(permissions)
            .createdAt(user.getCreatedAt())
            .build());
  }
}
