package com.ims.shared.auth;

public class TenantContext {
  private static final ThreadLocal<Long> TENANT = new InheritableThreadLocal<>();

  public static void setTenantId(Long tenantId) {
    TENANT.set(tenantId);
  }

  public static Long getTenantId() {
    return TENANT.get();
  }

  public static Long requireTenantId() {
    Long tenantId = TENANT.get();
    if (tenantId == null) {
      throw new IllegalStateException(
          "TenantContext is not set. "
              + "Ensure TenantContext.setTenantId() is called before accessing tenant-scoped data.");
    }
    return tenantId;
  }

  public static void clear() {
    TENANT.remove();
  }
}
