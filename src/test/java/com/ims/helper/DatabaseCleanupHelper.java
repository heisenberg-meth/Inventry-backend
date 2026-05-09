package com.ims.helper;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DatabaseCleanupHelper {
    private final JdbcTemplate jdbcTemplate;
    private static final String[] TABLES = {
            "order_items",
            "orders",
            "customers",
            "suppliers",
            "products",
            "categories",
            "users",
            "tenants",
            "roles",
            "permissions",
            "notifications",
            "alerts",
            "webhooks",
            "audit_logs",
            "payments",
            "subscriptions",
            "subscription_plans",
            "support_attachments",
            "support_messages",
            "support_tickets",
            "system_configs",
            "stock_movements",
            "inventory",
            "transfer_orders",
            "invoices"
    };

    public void cleanup() {
        if (jdbcTemplate == null)
            return;
        for (String table : TABLES) {
            try {
                jdbcTemplate.execute("DELETE FROM " + table + " WHERE 1=1");
            } catch (Exception expected) {
            }
        }
    }
}
