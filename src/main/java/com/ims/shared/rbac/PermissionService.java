package com.ims.shared.rbac;

import com.ims.model.Permission;
import com.ims.model.Role;
import com.ims.tenant.repository.RoleRepository;
import com.ims.tenant.repository.UserRepository;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionService {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;

  @Cacheable(value = "permissions", key = "#userId", cacheResolver = "tenantAwareCacheResolver")
  @Transactional(readOnly = true)
  public Set<String> getUserPermissions(Long userId, Long tenantId) {
    log.info("Fetching permissions from DB for user: {} tenantId: {}", userId, tenantId);

    Set<String> permissions = new HashSet<>();

    String roleName = userRepository.findRoleNameByUserId(userId).orElse(null);
    log.info("Role name for user {}: {}", userId, roleName);

    if (roleName != null) {
      Optional<Role> roleOpt;

      if (tenantId != null && !"ROOT".equals(roleName)) {
        roleOpt = roleRepository.findByNameWithPermissions(roleName, tenantId);
      } else {
        roleOpt = roleRepository.findByNameAndTenantIdIsNullWithPermissions(roleName);
      }

      if (roleOpt.isPresent()) {
        Role role = roleOpt.get();
        log.info("Found role: {} with {} permissions", role.getName(),
            role.getPermissions() != null ? role.getPermissions().size() : 0);
        if (role.getPermissions() != null && !role.getPermissions().isEmpty()) {
          permissions.addAll(
              role.getPermissions().stream()
                  .map(Permission::getKey)
                  .collect(Collectors.toSet()));
        }
      } else {
        log.info("Role '{}' not found for tenantId={}", roleName, tenantId);
      }
    }

    log.info("Permissions for user {}: {}", userId, permissions);
    return permissions;
  }
}