package com.ims.tenant.service;

import com.ims.dto.request.AssignPermissionsRequest;
import com.ims.dto.request.CreateUserRequest;
import com.ims.dto.response.UserResponse;
import com.ims.model.Permission;
import com.ims.model.Role;
import com.ims.model.User;
import com.ims.platform.repository.TenantRepository;
import com.ims.shared.audit.AuditLogService;
import com.ims.shared.auth.JwtAuthDetails;
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
import org.springframework.lang.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
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
    return userRepository.findAll(Objects.requireNonNull(pageable)).map(this::toResponse);
  }

  public @NonNull UserResponse getUserById(@NonNull Long id) {
    User user =
        Objects.requireNonNull(
            userRepository
                .findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new EntityNotFoundException("User not found")));
    return toResponse(user);
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
    Long tenantId = getTenantId();
    if (tenantId != null) {
      var tenant =
          tenantRepository
              .findById(tenantId)
              .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
      if (tenant.getMaxUsers() != null) {
        long currentCount = userRepository.countActive();
        if (currentCount >= tenant.getMaxUsers()) {
          throw new IllegalArgumentException(
              "User limit reached for your plan (" + tenant.getMaxUsers() + ")");
        }
      }
    }

    User user =
        User.builder()
            .name(request.getName())
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .role(request.getRole())
            .scope("TENANT")
            .isActive(true)
            .build();

    user = Objects.requireNonNull(userRepository.save(Objects.requireNonNull(user)));
    
    auditLogService.logAudit("CREATE", "USER", user.getId(), "Created user: " + user.getEmail() + " with role: " + user.getRole());
    
    log.info("User created: id={} email={} role={}", user.getId(), user.getEmail(), user.getRole());
    return toResponse(user);
  }

  @Transactional
  public @NonNull UserResponse updateRole(@NonNull Long id, @NonNull String newRole) {
    if (!VALID_TENANT_ROLES.contains(newRole)) {
      throw new IllegalArgumentException("Invalid role. Must be one of: " + VALID_TENANT_ROLES);
    }

    User user =
        Objects.requireNonNull(
            userRepository
                .findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new EntityNotFoundException("User not found")));

    user.setRole(newRole);
    user = Objects.requireNonNull(userRepository.save(Objects.requireNonNull(user)));
    
    auditLogService.logAudit("UPDATE_ROLE", "USER", id, "Updated role for user " + user.getEmail() + " to " + newRole);
    
    log.info("User role updated: id={} newRole={}", id, newRole);
    return toResponse(user);
  }

  @Transactional
  public UserResponse assignPermissions(@NonNull Long id, @NonNull AssignPermissionsRequest request) {
    User user = userRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException("User not found"));

    var perms = permissionRepository.findByIdIn(request.getPermissionIds());
    user.setCustomPermissions(new HashSet<>(perms));
    userRepository.save(user);

    auditLogService.logAudit("ASSIGN_PERMISSIONS", "USER", id, "Assigned " + perms.size() + " custom permissions to user: " + user.getEmail());
    
    return toResponse(user);
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

    auditLogService.logAudit("DEACTIVATE", "USER", id, "Deactivated user account: " + user.getEmail());
    log.info("User deactivated: id={}", id);
  }

  private Long getTenantId() {
    try {
      var auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth != null && auth.getDetails() instanceof JwtAuthDetails details) {
        return details.getTenantId();
      }
    } catch (Exception e) {
      log.trace("Caught expected exception in tenant id retrieval: {}", e.getMessage());
    }
    return null;
  }

  private @NonNull UserResponse toResponse(@NonNull User user) {
    Set<String> allPermissions = new HashSet<>();
    
    // 1. Role permissions
    if (user.getRole() != null) {
      Long tenantId = getTenantId();
      Optional<Role> roleOpt = tenantId != null 
          ? roleRepository.findByNameAndTenantId(user.getRole(), tenantId)
          : roleRepository.findByNameAndTenantIdIsNull(user.getRole());
      
      roleOpt.ifPresent(role -> 
          allPermissions.addAll(role.getPermissions().stream()
              .map(Permission::getKey)
              .collect(Collectors.toSet()))
      );
    }

    // 2. Custom permissions
    allPermissions.addAll(user.getCustomPermissions().stream()
        .map(Permission::getKey)
        .collect(Collectors.toSet()));

    return Objects.requireNonNull(UserResponse.builder()
        .id(user.getId())
        .name(user.getName())
        .email(user.getEmail())
        .role(user.getRole())
        .scope(user.getScope())
        .isActive(user.getIsActive())
        .permissions(new java.util.ArrayList<>(allPermissions))
        .createdAt(user.getCreatedAt())
        .build());
  }
}
