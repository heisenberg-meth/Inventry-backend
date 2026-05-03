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
    System.out.println("DEBUG PermissionService: Getting permissions for userId=" + userId + " tenantId=" + tenantId);
    Set<String> permissions = new HashSet<>();

    String roleName = userRepository.findRoleNameByUserId(userId).orElse(null);
    System.out.println("DEBUG PermissionService: roleName=" + roleName);

    if (roleName != null) {
      Optional<Role> roleOpt;

      if (tenantId != null && !"ROOT".equals(roleName)) {
        roleOpt = roleRepository.findByNameWithPermissions(roleName, tenantId.toString());
      } else {
        roleOpt = roleRepository.findByNameAndTenantIdIsNullWithPermissions(roleName);
      }

      if (roleOpt.isPresent()) {
        Role role = roleOpt.get();
        System.out.println("DEBUG PermissionService: Found role " + role.getName() + " with "
            + (role.getPermissions() != null ? role.getPermissions().size() : 0) + " permissions");
        if (role.getPermissions() != null && !role.getPermissions().isEmpty()) {
          permissions.addAll(
              role.getPermissions().stream()
                  .map(Permission::getKey)
                  .collect(Collectors.toSet()));
        }
      } else {
        System.out.println("DEBUG PermissionService: Role '" + roleName + "' not found for tenantId=" + tenantId);
      }
    }

    // FR-01-C: Fetch custom permissions directly from User entity
    userRepository.findByIdWithPermissions(userId).ifPresent(user -> {
      if (user.getCustomPermissions() != null && !user.getCustomPermissions().isEmpty()) {
        Set<String> customPerms = user.getCustomPermissions().stream()
            .map(Permission::getKey)
            .collect(Collectors.toSet());
        System.out.println(
            "DEBUG PermissionService: Adding " + customPerms.size() + " custom permissions for user " + userId);
        permissions.addAll(customPerms);
      }
    });

    System.out.println("DEBUG PermissionService: Final permissions for user " + userId + ": " + permissions);
    return permissions;
  }
}