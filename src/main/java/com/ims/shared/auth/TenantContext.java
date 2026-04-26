package com.ims.shared.auth;

public class TenantContext {
  /**
   * Sentinel for system-wide operations that bypass tenant isolation (e.g. platform admin,
   * background maintenance). Use with extreme caution.
   */
  public static final Long PLATFORM_TENANT_ID = -1L;

  private static final ThreadLocal<Long> TENANT = new ThreadLocal<>();

  public static void setTenantId(Long tenantId) {
    TENANT.set(tenantId);
  }

  public static Long getTenantId() {
    return TENANT.get();
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
