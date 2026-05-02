package com.ims.shared.logging;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import com.ims.shared.auth.TenantContext;

public class MdcTaskDecorator implements TaskDecorator {

  @Override
  public Runnable decorate(Runnable runnable) {
    Map<String, String> contextMap = MDC.getCopyOfContextMap();
    Long tenantId = TenantContext.getTenantId();
    return () -> {
      try {
        if (contextMap != null) {
          MDC.setContextMap(contextMap);
        }
        if (tenantId != null) {
          TenantContext.setTenantId(tenantId);
        }
        runnable.run();
      } finally {
        TenantContext.clear();
        MDC.clear();
      }
    };
  }
}
