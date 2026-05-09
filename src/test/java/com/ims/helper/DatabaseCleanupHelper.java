package com.ims.helper;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DatabaseCleanupHelper {
    private final JdbcTemplate jdbcTemplate;

    public void cleanup() {
        if (jdbcTemplate == null)
            return;

        String[] CASCADE_TABLES = {
            "order_items", "orders", "customers", "suppliers", "products", "categories",
            "users", "tenants", "roles", "permissions", "notifications", "alerts",
            "webhooks", "payments", "subscriptions", "subscription_plans",
            "support_attachments", "support_messages", "support_tickets",
            "system_configs", "stock_movements", "inventory", "transfer_orders", "invoices"
        };

        try {
            jdbcTemplate.execute("SET session_replication_role = 'replica';");
        } catch (Exception e) {}

        try {
            for (String table : CASCADE_TABLES) {
                jdbcTemplate.execute("TRUNCATE TABLE " + table + " CASCADE");
            }
            jdbcTemplate.execute("TRUNCATE TABLE audit_logs CASCADE");
        } catch (Exception e) {
            for (String table : CASCADE_TABLES) {
                try {
                    jdbcTemplate.execute("DELETE FROM " + table);
                } catch (Exception ignored) {}
            }
            try {
                jdbcTemplate.execute("DELETE FROM audit_logs");
            } catch (Exception ignored) {}
        } finally {
            try {
                jdbcTemplate.execute("SET session_replication_role = 'origin';");
            } catch (Exception e) {}
        }
    }
}
