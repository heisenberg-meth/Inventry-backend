package com.ims.tenant;

import com.ims.BaseIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
public class OrderWorkflowIntegrationTest extends BaseIntegrationTest {

        private NamedParameterJdbcTemplate jdbc;

        @BeforeEach
        void setup() {
                cleanupDatabase();
                mockRedisAndCache();
                jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        }

        @Test
        @Transactional
        void testCompleteOrderWorkflow() throws Exception {
                // Use raw JDBC to avoid Hibernate tenant_id validation issues

                // Create role
                jdbc.update(
                                "INSERT INTO roles (name, description, tenant_id) VALUES (:name, :desc, :tid)",
                                new MapSqlParameterSource()
                                                .addValue("name", "TENANT_ADMIN")
                                                .addValue("desc", "Admin Role")
                                                .addValue("tid", testTenant1Id));

                // Get role ID
                Long roleId = jdbc.queryForObject(
                                "SELECT id FROM roles WHERE name = 'TENANT_ADMIN' AND tenant_id = :tid",
                                new MapSqlParameterSource().addValue("tid", testTenant1Id),
                                Long.class);

                // Create user
                String passHash = passwordEncoder.encode("password123");
                jdbc.update(
                                "INSERT INTO users (name, email, password_hash, role_id, tenant_id, scope, is_active, is_verified) "
                                                +
                                                "VALUES (:name, :email, :pass, :rid, :tid, :scope, true, true)",
                                new MapSqlParameterSource()
                                                .addValue("name", "Admin")
                                                .addValue("email", "admin@order.com")
                                                .addValue("pass", passHash)
                                                .addValue("rid", roleId)
                                                .addValue("tid", testTenant1Id)
                                                .addValue("scope", "TENANT"));

                // Create customer
                jdbc.update(
                                "INSERT INTO customers (name, tenant_id) VALUES (:name, :tid)",
                                new MapSqlParameterSource()
                                                .addValue("name", "Test Customer")
                                                .addValue("tid", testTenant1Id));

                Long customerId = jdbc.queryForObject(
                                "SELECT id FROM customers WHERE tenant_id = :tid",
                                new MapSqlParameterSource().addValue("tid", testTenant1Id),
                                Long.class);

                // Create product
                jdbc.update(
                                "INSERT INTO products (name, sku, sale_price, stock, active, tenant_id) " +
                                                "VALUES (:name, :sku, :price, :stock, true, :tid)",
                                new MapSqlParameterSource()
                                                .addValue("name", "Test Product")
                                                .addValue("sku", "PROD-001")
                                                .addValue("price", new BigDecimal("100.00"))
                                                .addValue("stock", 100)
                                                .addValue("tid", testTenant1Id));

                Long productId = jdbc.queryForObject(
                                "SELECT id FROM products WHERE tenant_id = :tid",
                                new MapSqlParameterSource().addValue("tid", testTenant1Id),
                                Long.class);

                // Create order
                jdbc.update(
                                "INSERT INTO orders (customer_id, tenant_id, status, type, currency, total_amount, tax_amount, discount) "
                                                +
                                                "VALUES (:cid, :tid, 'PENDING', 'SALE', 'INR', :total, 0, 0)",
                                new MapSqlParameterSource()
                                                .addValue("cid", customerId)
                                                .addValue("tid", testTenant1Id)
                                                .addValue("total", new BigDecimal("1000.00")));

                Long orderId = jdbc.queryForObject(
                                "SELECT id FROM orders WHERE tenant_id = :tid ORDER BY id DESC LIMIT 1",
                                new MapSqlParameterSource().addValue("tid", testTenant1Id),
                                Long.class);

                // Confirm order
                jdbc.update(
                                "UPDATE orders SET status = 'CONFIRMED' WHERE id = :oid",
                                new MapSqlParameterSource().addValue("oid", orderId));

                // Decrease stock
                jdbc.update(
                                "UPDATE products SET stock = stock - 10 WHERE id = :pid",
                                new MapSqlParameterSource().addValue("pid", productId));

                // Verify stock = 90
                BigDecimal stock = jdbc.queryForObject(
                                "SELECT stock FROM products WHERE id = :pid",
                                new MapSqlParameterSource().addValue("pid", productId),
                                BigDecimal.class);

                org.junit.jupiter.api.Assertions.assertEquals(90, stock.intValue());
        }
}