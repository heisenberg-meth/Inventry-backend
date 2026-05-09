package com.ims.helper;

import com.ims.shared.auth.TenantContext;
import java.util.function.Supplier;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class TenantTestHelper {

    public void withTenant(Long tenantId, Runnable action) {
        try {
            TenantContext.setTenantId(tenantId);
            MDC.put("tenantId", String.valueOf(tenantId));
            action.run();
        } finally {
            TenantContext.clear();
            MDC.remove("tenantId");
        }
    }

    public <T> T withTenant(Long tenantId, Supplier<T> action) {
        try {
            TenantContext.setTenantId(tenantId);
            MDC.put("tenantId", String.valueOf(tenantId));
            return action.get();
        } finally {
            TenantContext.clear();
            MDC.remove("tenantId");
        }
    }

    public void clear() {
        TenantContext.clear();
        MDC.remove("tenantId");
    }
}
