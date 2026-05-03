package com.ims.tenant;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.ims.BaseIntegrationTest;
import com.ims.tenant.dto.OrderItemRequest;
import com.ims.tenant.dto.OrderRequest;
import com.ims.tenant.repository.OrderRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ims.shared.auth.TenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class OrderIdempotencyIntegrationTest extends BaseIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private OrderRepository orderRepository;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        private Long testSupplierId;
        private Long testProductId;

        @BeforeEach
        @Override
        protected void setUp() throws Exception {
                super.setUp();
                cleanupDatabase();

                // Create a supplier and product for the test
                TenantContext.setTenantId(testTenant1Id);
                Long supplierId = jdbcTemplate.queryForObject(
                                "INSERT INTO suppliers (name, tenant_id, version) VALUES ('Test Supplier', ?, 0) RETURNING id",
                                Long.class, testTenant1Id);
                Long catId = jdbcTemplate.queryForObject(
                                "INSERT INTO categories (name, tenant_id, version) VALUES ('Test Cat', ?, 0) RETURNING id",
                                Long.class, testTenant1Id);
                this.testSupplierId = supplierId;
                this.testProductId = jdbcTemplate.queryForObject(
                                "INSERT INTO products (name, sku, tenant_id, category_id, sale_price, version)" +
                                                " VALUES ('Test Prod', 'SKU1', ?, ?, 100.00, 0) RETURNING id",
                                Long.class, testTenant1Id, catId);
                TenantContext.clear();
        }

        @Test
        void testOrderIdempotency() throws Exception {
                String token = getAdminToken();
                Long tenantId = getTenantId();

                OrderRequest request = new OrderRequest();
                request.setSupplierId(testSupplierId);
                OrderItemRequest item = new OrderItemRequest();
                item.setProductId(testProductId);
                item.setQuantity(10);
                item.setUnitPrice(new BigDecimal("100.00"));
                request.setItems(List.of(item));

                String idempotencyKey = UUID.randomUUID().toString();

                // First request
                String firstResponse = mockMvc.perform(post("/api/v1/tenant/orders/purchase")
                                .header("X-Tenant-ID", tenantId)
                                .header("Authorization", "Bearer " + token)
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Map<String, Object> firstOrder = objectMapper.readValue(firstResponse,
                                new TypeReference<Map<String, Object>>() {
                                });

                // Second request with same key
                String secondResponse = mockMvc.perform(post("/api/v1/tenant/orders/purchase")
                                .header("X-Tenant-ID", tenantId)
                                .header("Authorization", "Bearer " + token)
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Map<String, Object> secondOrder = objectMapper.readValue(secondResponse,
                                new TypeReference<Map<String, Object>>() {
                                });

                assertEquals(((Number) firstOrder.get("id")).longValue(), ((Number) secondOrder.get("id")).longValue(),
                                "Should return the same order ID");

                assertTrue(orderRepository.findByIdempotencyKey(idempotencyKey).isPresent());
        }

        private Long getTenantId() {
                return testTenant1Id;
        }

        private String getAdminToken() throws Exception {
                return login("root@ims.com", TEST_ROOT_PASSWORD, "PLATFORM", systemTenantId);
        }
}
