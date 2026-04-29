package com.ims.shared.rbac;

import com.ims.model.Permission;
import com.ims.model.Role;
import com.ims.model.User;
import com.ims.tenant.repository.RoleRepository;
import com.ims.tenant.repository.UserRepository;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionService {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;

  /**
   * Fetch all permissions for a user, including those from their role and custom permissions.
   * Cached for 5 minutes (configured in RedisConfig). Cache key is tenant-aware.
   */
  @Cacheable(value = "permissions", key = "#userId", cacheResolver = "tenantAwareCacheResolver")
  @Transactional(readOnly = true)
  public Set<String> getUserPermissions(Long userId, Long tenantId) {
    log.info("Fetching permissions from DB for user: {}", userId);

    User user =
        userRepository
            .findByIdWithPermissions(userId)
            .orElseThrow(() -> new AccessDeniedException("User not found"));

    Set<String> permissions = new HashSet<>();

    // 1. Get permissions from Role
    if (user.getRole() != null) {
      Optional<Role> roleOpt =
          tenantId != null
              ? roleRepository.findByNameWithPermissions(user.getRole().getName())
              : roleRepository.findByNameAndTenantIdIsNullWithPermissions(user.getRole().getName());

      roleOpt.ifPresent(
          role ->
              permissions.addAll(
                  role.getPermissions().stream()
                      .map(Permission::getKey)
                      .collect(Collectors.toSet())));
    }

    // 2. Get custom permissions
    permissions.addAll(
        user.getCustomPermissions().stream().map(Permission::getKey).collect(Collectors.toSet()));

    return permissions;
  }
}
