package com.ims.shared.logging;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;

public class MdcTaskDecorator implements TaskDecorator {

  @Override
  @NonNull
  public Runnable decorate(@NonNull Runnable runnable) {
    Map<String, String> contextMap = MDC.getCopyOfContextMap();
    Long tenantId = com.ims.shared.auth.TenantContext.getTenantId();
    return () -> {
      try {
        if (contextMap != null) {
          MDC.setContextMap(contextMap);
        }
        com.ims.shared.auth.TenantContext.setTenantId(tenantId);
        runnable.run();
      } finally {
        com.ims.shared.auth.TenantContext.clear();
        MDC.clear();
      }
    };
  }
}
