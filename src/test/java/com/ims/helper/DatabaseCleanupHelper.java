package com.ims.helper;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DatabaseCleanupHelper {
  private final JdbcTemplate jdbcTemplate;

  public void cleanup() {
    if (jdbcTemplate == null) return;

    // We specifically EXCLUDE 'permissions' and 'system_configs' as they contain global seed data
    // from migrations that the application requires to function.
    String[] CASCADE_TABLES = {
      "order_items",
      "orders",
      "customers",
      "suppliers",
      "products",
      "categories",
      "users",
      "tenants",
      "roles",
      "notifications",
      "alerts",
      "webhooks",
      "payments",
      "subscriptions",
      "subscription_plans",
      "support_attachments",
      "support_messages",
      "support_tickets",
      "stock_movements",
      "inventory",
      "transfer_orders",
      "invoices",
      "user_permissions",
      "role_permissions"
    };

    try {
      jdbcTemplate.execute("SET session_replication_role = 'replica';");
    } catch (Exception e) {
    }

    try {
      for (String table : CASCADE_TABLES) {
        jdbcTemplate.execute("TRUNCATE TABLE " + table + " CASCADE");
      }
      jdbcTemplate.execute("TRUNCATE TABLE audit_logs CASCADE");
    } catch (Exception e) {
      // Fallback to DELETE if TRUNCATE fails (e.g. permission issues or non-postgres)
      for (String table : CASCADE_TABLES) {
        try {
          jdbcTemplate.execute("DELETE FROM " + table);
        } catch (Exception ignored) {
        }
      }
      try {
        jdbcTemplate.execute("DELETE FROM audit_logs");
      } catch (Exception ignored) {
      }
    } finally {
      try {
        jdbcTemplate.execute("SET session_replication_role = 'origin';");
      } catch (Exception e) {
      }
    }
  }
}
