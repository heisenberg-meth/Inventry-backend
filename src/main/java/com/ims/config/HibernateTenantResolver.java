package com.ims.config;

import com.ims.shared.auth.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

@Component
public class HibernateTenantResolver implements CurrentTenantIdentifierResolver<Long> {

    @Override
    public Long resolveCurrentTenantIdentifier() {
        Long tenantId = TenantContext.getTenantId();

        // 🔥 FORCE fallback
        if (tenantId == null) {
            return 1L;  // hardcode for now
        }

        return tenantId;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
