package com.ims.shared.rbac;

import com.ims.model.Permission;
import com.ims.model.Role;
import com.ims.model.User;
import com.ims.platform.service.SystemConfigService;
import com.ims.shared.auth.JwtAuthDetails;
import com.ims.tenant.repository.RoleRepository;
import com.ims.tenant.repository.UserRepository;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class RbacAspect {

  private final SystemConfigService systemConfigService;
  private final UserRepository userRepository;
  private final RoleRepository roleRepository;

  private boolean isRoot() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getDetails() instanceof JwtAuthDetails details) {
      return "ROOT".equals(details.getRole());
    }
    return false;
  }

  @Around("@annotation(requiresRole)")
  public Object checkRole(ProceedingJoinPoint pjp, RequiresRole requiresRole) throws Throwable {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      throw new AccessDeniedException("Not authenticated");
    }

    var userAuthorities = auth.getAuthorities();
    if (userAuthorities == null || userAuthorities.isEmpty()) {
      throw new AccessDeniedException("No roles assigned to user");
    }

    String[] requiredRoles = requiresRole.value();
    boolean allowed =
        userAuthorities.stream()
            .anyMatch(
                grantedAuthority ->
                    Arrays.asList(requiredRoles).contains(grantedAuthority.getAuthority()));

    // Allow ROOT if Support Mode is enabled
    if (!allowed && isRoot() && systemConfigService.isSupportModeEnabled()) {
      allowed = true;
    }

    if (!allowed) {
      throw new AccessDeniedException(
          "Access denied: Required "
              + Arrays.toString(requiredRoles)
              + ", but user has authorities "
              + userAuthorities);
    }

    return pjp.proceed();
  }

  @Around("@annotation(requiresPermission)")
  public Object checkPermission(ProceedingJoinPoint pjp, RequiresPermission requiresPermission) throws Throwable {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      throw new AccessDeniedException("Not authenticated");
    }

    if (auth.getDetails() instanceof JwtAuthDetails details) {
      Long userId = details.getUserId();
      Long tenantId = details.getTenantId();

      User user = userRepository.findByIdWithPermissions(userId)
          .orElseThrow(() -> new AccessDeniedException("User not found"));

      Set<String> permissions = new HashSet<>();
      
      // 1. Get permissions from Role
      if (user.getRole() != null) {
        Optional<Role> roleOpt = tenantId != null 
            ? roleRepository.findByNameAndTenantIdWithPermissions(user.getRole(), tenantId)
            : roleRepository.findByNameAndTenantIdIsNullWithPermissions(user.getRole());
        
        roleOpt.ifPresent(role -> 
            permissions.addAll(role.getPermissions().stream()
                .map(Permission::getKey)
                .collect(Collectors.toSet()))
        );
      }

      // 2. Get custom permissions
      permissions.addAll(user.getCustomPermissions().stream()
          .map(Permission::getKey)
          .collect(Collectors.toSet()));

      String requiredPermission = requiresPermission.value();
      boolean allowed = permissions.contains(requiredPermission);

      // ROOT override
      if (!allowed && "ROOT".equals(user.getRole()) && systemConfigService.isSupportModeEnabled()) {
        allowed = true;
      }

      if (!allowed) {
        throw new AccessDeniedException("Access denied: Missing required permission " + requiredPermission);
      }
    } else {
      throw new AccessDeniedException("Invalid authentication details");
    }

    return pjp.proceed();
  }
}
