package com.ims.config;

import com.ims.shared.auth.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")

public class HibernateTenantResolver implements CurrentTenantIdentifierResolver<Long> {

    @Override
    public Long resolveCurrentTenantIdentifier() {
        Long tenantId = TenantContext.getTenantId();
        return tenantId != null ? tenantId : 0L;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
