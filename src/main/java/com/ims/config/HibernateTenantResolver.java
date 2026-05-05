package com.ims.config;

import com.ims.shared.auth.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class HibernateTenantResolver implements CurrentTenantIdentifierResolver<Long> {

    private final Environment environment;

    public HibernateTenantResolver(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Long resolveCurrentTenantIdentifier() {
        Long tenantId = TenantContext.getTenantId();

        if (tenantId == null) {
            if (environment.acceptsProfiles(org.springframework.core.env.Profiles.of("test"))) {
                return 1L;
            }
            // Return 0L during startup/system operations where no tenant is present
            return 0L;
        }

        return tenantId;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
