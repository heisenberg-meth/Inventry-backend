package com.ims.shared.rbac;

import java.util.Arrays;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RbacAspect {

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
