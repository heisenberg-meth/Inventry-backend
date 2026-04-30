package com.ims.shared.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class BusinessMetrics {

    private final Counter ordersTotal;
    private final Counter salesTotal;
    private final Counter purchasesTotal;
    private final Counter webhookEventsTotal;
    private final Counter loginFailuresTotal;

    public BusinessMetrics(MeterRegistry registry) {
        this.ordersTotal = Counter.builder("ims.orders.total")
                .description("Total number of orders processed")
                .register(registry);

        this.salesTotal = Counter.builder("ims.orders.sales.total")
                .description("Total number of sales orders")
                .register(registry);

        this.purchasesTotal = Counter.builder("ims.orders.purchases.total")
                .description("Total number of purchase orders")
                .register(registry);

        this.webhookEventsTotal = Counter.builder("ims.webhooks.events.total")
                .description("Total number of webhook events generated")
                .register(registry);

        this.loginFailuresTotal = Counter.builder("ims.auth.login.failures.total")
                .description("Total number of failed login attempts")
                .register(registry);
    }

    public void incrementOrders(String type) {
        ordersTotal.increment();
        if ("SALE".equals(type)) {
            salesTotal.increment();
        } else if ("PURCHASE".equals(type)) {
            purchasesTotal.increment();
        }
    }

    public void incrementWebhookEvents() {
        webhookEventsTotal.increment();
    }

    public void incrementLoginFailures() {
        loginFailuresTotal.increment();
    }
}
