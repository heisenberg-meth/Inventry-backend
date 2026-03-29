package com.ims.shared.rbac;

import com.ims.platform.service.SystemConfigService;
import com.ims.shared.auth.JwtAuthDetails;
import java.util.Arrays;
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
}
