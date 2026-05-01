package com.ims.shared.rbac;

import com.ims.platform.service.SystemConfigService;
import com.ims.shared.auth.JwtAuthDetails;
import java.util.Arrays;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Profile("!test")
@RequiredArgsConstructor
public class RbacAspect {

  private final SystemConfigService systemConfigService;
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

    var authorities = auth.getAuthorities();
    if (authorities == null || authorities.isEmpty()) {
      throw new AccessDeniedException("No roles assigned to user");
    }

    String[] requiredRoles = requiresRole.value();
    boolean allowed = authorities.stream()
        .anyMatch(
            grantedAuthority -> Arrays.asList(requiredRoles).contains(grantedAuthority.getAuthority()));

    // Allow ROOT if Support Mode is enabled
    if (!allowed && isRoot() && systemConfigService.isSupportModeEnabled()) {
      allowed = true;
    }

    if (!allowed) {
      throw new AccessDeniedException(
          "Access denied: Required "
              + Arrays.toString(requiredRoles)
              + ", but user has authorities "
              + authorities);
    }

    return pjp.proceed();
  }

  @Around("@annotation(requiresPermission)")
  public Object checkPermission(ProceedingJoinPoint pjp, RequiresPermission requiresPermission)
      throws Throwable {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      throw new AccessDeniedException("Not authenticated");
    }

    if (auth.getDetails() instanceof JwtAuthDetails details) {
      Long userId = details.getUserId();
      Long tenantId = details.getTenantId();

      // Resolve permissions from PermissionService (Redis Cache -> DB)
      // Always use the service to ensure permissions are up-to-date and never stale.
      Set<String> permissions = permissionService.getUserPermissions(userId, tenantId);

      String requiredPermission = requiresPermission.value();
      boolean allowed = permissions.contains(requiredPermission);

      // ROOT override
      if (!allowed
          && "ROOT".equals(details.getRole())
          && systemConfigService.isSupportModeEnabled()) {
        allowed = true;
      }

      if (!allowed) {
        throw new AccessDeniedException(
            "Access denied: Missing required permission " + requiredPermission);
      }
    } else {
      throw new AccessDeniedException("Invalid authentication details");
    }

    return pjp.proceed();
  }
}
