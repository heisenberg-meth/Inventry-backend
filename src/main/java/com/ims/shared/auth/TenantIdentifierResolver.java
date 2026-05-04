package com.ims.shared.auth;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException(
                    "No tenant ID set in TenantContext. Ensure JwtFilter runs before Hibernate operations.");
        }
        return tenantId.toString();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}