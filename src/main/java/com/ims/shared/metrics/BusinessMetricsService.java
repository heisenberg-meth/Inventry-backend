package com.ims.shared.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@NoArgsConstructor(force = true)
@Slf4j
public class BusinessMetricsService {

    private final MeterRegistry meterRegistry;

    private final Counter ordersCreatedCounter;
    private final Counter ordersFailedCounter;
    private final Counter invoicesGeneratedCounter;
    private final Counter lowStockAlertsCounter;
    private final Counter customersCreatedCounter;
    private final Counter productsCreatedCounter;
    private final Timer orderCreationTimer;
    private final Timer invoiceGenerationTimer;
    private final Counter paymentsFailedCounter;
    private volatile int activeTenantsCount = 0;

    public BusinessMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.ordersCreatedCounter = Counter.builder("business.orders.created")
                .description("Total orders created")
                .register(meterRegistry);

        this.ordersFailedCounter = Counter.builder("business.orders.failed")
                .description("Total orders that failed")
                .register(meterRegistry);

        this.invoicesGeneratedCounter = Counter.builder("business.invoices.generated")
                .description("Total invoices generated")
                .register(meterRegistry);

        this.lowStockAlertsCounter = Counter.builder("business.inventory.low_stock_alerts")
                .description("Total low stock alerts triggered")
                .register(meterRegistry);

        this.customersCreatedCounter = Counter.builder("business.customers.created")
                .description("Total customers created")
                .register(meterRegistry);

        this.productsCreatedCounter = Counter.builder("business.products.created")
                .description("Total products created")
                .register(meterRegistry);

        this.orderCreationTimer = Timer.builder("business.orders.creation.latency")
                .description("Order creation latency")
                .register(meterRegistry);

        this.invoiceGenerationTimer = Timer.builder("business.invoices.generation.latency")
                .description("Invoice generation latency")
                .register(meterRegistry);

        this.paymentsFailedCounter = Counter.builder("business.payments.failed")
                .description("Total failed payments")
                .register(meterRegistry);

        io.micrometer.core.instrument.Gauge
                .builder("business.platform.active_tenants.gauge", this, v -> v.activeTenantsCount)
                .register(meterRegistry);
    }

    public void incrementFailedPayments() {
        paymentsFailedCounter.increment();
    }

    public void setActiveTenants(int count) {
        activeTenantsCount = count;
    }

    public void incrementOrdersCreated() {
        ordersCreatedCounter.increment();
    }

    public void incrementOrdersFailed() {
        ordersFailedCounter.increment();
    }

    public void incrementInvoicesGenerated() {
        invoicesGeneratedCounter.increment();
    }

    public void incrementLowStockAlerts() {
        lowStockAlertsCounter.increment();
    }

    public void incrementCustomersCreated() {
        customersCreatedCounter.increment();
    }

    public void incrementProductsCreated() {
        productsCreatedCounter.increment();
    }

    public Timer.Sample startOrderTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordOrderTimer(Timer.Sample sample) {
        sample.stop(orderCreationTimer);
    }

    public Timer.Sample startInvoiceTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordInvoiceTimer(Timer.Sample sample) {
        sample.stop(invoiceGenerationTimer);
    }
}