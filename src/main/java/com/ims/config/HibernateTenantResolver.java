package com.ims.config;

import com.ims.shared.auth.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

@Component
public class HibernateTenantResolver implements CurrentTenantIdentifierResolver<Long> {

    @Override
    public Long resolveCurrentTenantIdentifier() {
        Long tenantId = TenantContext.getTenantId();

        if (tenantId == null) {
            // Return a default/system tenant ID to allow startup and validation to pass.
            // In a real multi-tenant app, you might want to return a 'null' or a special ID
            // that represents 'system' or 'all' depending on your requirements.
            return 0L;
        }

        return tenantId;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
