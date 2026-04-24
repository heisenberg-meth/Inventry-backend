package com.ims.util;

import com.ims.shared.auth.TenantContext;

public class CacheKeyUtil {
  public static String tenantKey(String suffix) {
    return TenantContext.getTenantId() + ":" + suffix;
  }
}
