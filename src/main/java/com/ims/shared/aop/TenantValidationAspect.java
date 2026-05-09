package com.ims.shared.aop;

import com.ims.shared.auth.TenantContext;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Aspect
@Component
public class TenantValidationAspect {

  private static final Logger log = LoggerFactory.getLogger(TenantValidationAspect.class);

  @Before(
      "execution(* com.ims..service..*(..)) && @annotation(transactional) && !execution(* com.ims.shared.auth..*(..))")
  public void validateTenantContext(JoinPoint joinPoint, Transactional transactional) {
    Long tenantId = TenantContext.getTenantId();

    // If TenantContext is empty, check if the first argument is a Long (convention
    // for tenantId)
    if (tenantId == null) {
      Object[] args = joinPoint.getArgs();
      if (args != null && args.length > 0 && args[0] instanceof Long) {
        tenantId = (Long) args[0];
        log.debug("TenantId resolved from method argument: {}", tenantId);
      }
    }

    if (tenantId == null) {
      log.error(
          "TENANT ISOLATION VIOLATION: TenantContext not set and no tenantId argument for service method: {}.{}(), transaction will fail",
          joinPoint.getSignature().getDeclaringTypeName(),
          joinPoint.getSignature().getName());
      throw new IllegalStateException(
          "Tenant ID not set in TenantContext. Request must include valid JWT token with tenant_id claim.");
    }

    log.debug(
        "TenantContext validated for tenantId={}, method={}.{}",
        tenantId,
        joinPoint.getSignature().getDeclaringTypeName(),
        joinPoint.getSignature().getName());
  }
}
