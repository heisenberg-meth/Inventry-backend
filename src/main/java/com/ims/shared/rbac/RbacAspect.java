package com.ims.shared.rbac;

import com.ims.platform.service.SystemConfigService;
import com.ims.shared.auth.JwtAuthDetails;
import com.ims.tenant.repository.RoleRepository;
import com.ims.tenant.repository.UserRepository;
import java.util.Arrays;
import java.util.Set;
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
  private final PermissionService permissionService;

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

      // HYBRID APPROACH:
      // 1. Try permissions from JWT (primary)
      Set<String> permissions = details.getPermissions();

      // 2. Fallback to PermissionService (Redis Cache -> DB)
      // We check if permissions are null or empty. 
      // Note: PLATFORM users might have empty permissions if not set, 
      // but we still want to hit the cache/DB once if JWT is empty.
      if (permissions == null || permissions.isEmpty()) {
        permissions = permissionService.getUserPermissions(userId, tenantId);
      }

      String requiredPermission = requiresPermission.value();
      boolean allowed = permissions.contains(requiredPermission);

      // ROOT override
      if (!allowed && "ROOT".equals(details.getRole()) && systemConfigService.isSupportModeEnabled()) {
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
