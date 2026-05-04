package com.ims.shared.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class BusinessMetricsService {

    private final Counter ordersCreated;
    private final Counter lowStockAlerts;
    private final Counter failedPayments;
    private final Counter activeTenants;

    public BusinessMetricsService(MeterRegistry meterRegistry) {
        this.ordersCreated = Counter.builder("ims.orders.created")
                .description("Number of orders created")
                .tag("type", "business")
                .register(meterRegistry);
        
        this.lowStockAlerts = Counter.builder("ims.alerts.low_stock")
                .description("Number of low stock alerts triggered")
                .tag("type", "business")
                .register(meterRegistry);
        
        this.failedPayments = Counter.builder("ims.payments.failed")
                .description("Number of failed payments")
                .tag("type", "business")
                .register(meterRegistry);
        
        this.activeTenants = Counter.builder("ims.tenants.active")
                .description("Number of active tenants")
                .tag("type", "business")
                .register(meterRegistry);
    }

    public void incrementOrdersCreated() {
        ordersCreated.increment();
    }

    public void incrementLowStockAlerts() {
        lowStockAlerts.increment();
    }

    public void incrementFailedPayments() {
        failedPayments.increment();
    }

    public void setActiveTenants(double count) {
        activeTenants.increment(count);
    }
}
