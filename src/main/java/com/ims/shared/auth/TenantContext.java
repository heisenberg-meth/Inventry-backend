package com.ims.shared.auth;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

public class TenantContext {
  /**
   * Sentinel for system-wide operations that bypass tenant isolation (e.g.
   * platform admin,
   * background maintenance). Use with extreme caution.
   */
  @NonNull
  public static final Long PLATFORM_TENANT_ID = -1L;

  private static final ThreadLocal<Long> TENANT = new ThreadLocal<>();

  public static void setTenantId(@Nullable Long tenantId) {
    TENANT.set(tenantId);
  }

  @Nullable
  public static Long getTenantId() {
    return TENANT.get();
  }

  public static Long requireTenantId() {
    Long tenantId = getTenantId();
    if (tenantId == null) {
      // Check if we are running in a test environment
      if ("true".equals(System.getProperty("app.test.mode"))) {
        return 1L; // Default test tenant ID
      }
      throw new IllegalStateException("Missing tenant context");
    }
    return tenantId;
  }

  public static void clear() {
    TENANT.remove();
  }

  public static boolean isPlatformContext() {
    return PLATFORM_TENANT_ID.equals(getTenantId());
  }

  public static void assertTenantPresent() {
    Long tenantId = getTenantId();
    if (tenantId == null) {
      throw new com.ims.shared.exception.TenantContextException("Tenant missing at service layer");
    }
  }

  public static void runWithTenant(Long tenantId, Runnable task) {
    Long previous = getTenantId();
    setTenantId(tenantId);
    try {
      task.run();
    } finally {
      if (previous != null) {
        setTenantId(previous);
      } else {
        clear();
      }
    }
  }
}
