package com.ims.shared.auth;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class TenantEnforcementAspect {

    @Before("@annotation(org.springframework.transaction.annotation.Transactional) || @within(org.springframework.transaction.annotation.Transactional)")
    public void validateTenant() {
        if (TenantContext.getTenantId() == null) {
            log.error("Security Violation: Database operation attempted without TenantContext");
            throw new IllegalStateException(
                    "Tenant not set before DB operation. This is required for multi-tenant data isolation.");
        }
    }
}
