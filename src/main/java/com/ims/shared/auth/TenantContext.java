package com.ims.shared.auth;

public class TenantContext {
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
}
