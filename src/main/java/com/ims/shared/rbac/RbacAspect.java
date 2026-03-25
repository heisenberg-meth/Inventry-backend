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
    String currentRole =
        SecurityContextHolder.getContext()
            .getAuthentication()
            .getAuthorities()
            .iterator()
            .next()
            .getAuthority();

    boolean allowed = Arrays.asList(requiresRole.value()).contains(currentRole);
    if (!allowed) {
      throw new AccessDeniedException(
          "Required: " + Arrays.toString(requiresRole.value()) + ", Got: " + currentRole);
    }

    return pjp.proceed();
  }
}
