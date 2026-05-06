package com.ims.shared.metrics;

import com.ims.shared.auth.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BusinessMetricsService {

  private final MeterRegistry meterRegistry;

  private static final String TAG_TENANT_ID = "tenantId";
  private static final String TAG_TYPE = "type";
  private static final String TYPE_BUSINESS = "business";

  public void incrementOrdersCreated() {
    String tenantId = String.valueOf(TenantContext.getTenantId());
    meterRegistry.counter("ims.orders.created", 
        TAG_TENANT_ID, tenantId, 
        TAG_TYPE, TYPE_BUSINESS)
        .increment();
  }

  public void incrementLowStockAlerts() {
    String tenantId = String.valueOf(TenantContext.getTenantId());
    meterRegistry.counter("ims.alerts.low_stock", 
        TAG_TENANT_ID, tenantId, 
        TAG_TYPE, TYPE_BUSINESS)
        .increment();
  }

  public void incrementFailedPayments() {
    String tenantId = String.valueOf(TenantContext.getTenantId());
    meterRegistry.counter("ims.payments.failed", 
        TAG_TENANT_ID, tenantId, 
        TAG_TYPE, TYPE_BUSINESS)
        .increment();
  }

  public void setActiveTenants(double count) {
    // Active tenants is a global metric, but we can still tag it or use a gauge
    meterRegistry.counter("ims.tenants.active", 
        TAG_TYPE, TYPE_BUSINESS)
        .increment(count);
  }
}
