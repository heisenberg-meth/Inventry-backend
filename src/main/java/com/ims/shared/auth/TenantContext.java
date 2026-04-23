package com.ims.shared.auth;

public class TenantContext {
  public static final Long SYSTEM_TENANT_ID = 0L;
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

  public static void assertTenantPresent() {
    if (getTenantId() == null) {
      throw new com.ims.shared.exception.TenantContextException("Tenant missing at service layer");
    }
  }

  public static void runWithTenant(Long tenantId, Runnable task) {
    setTenantId(tenantId);
    try {
      task.run();
    } finally {
      clear();
    }
  }
}
