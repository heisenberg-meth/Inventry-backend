package com.ims.tenant;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ims.BaseIntegrationTest;
import com.ims.model.Order;
import com.ims.tenant.dto.OrderItemRequest;
import com.ims.tenant.dto.OrderRequest;
import com.ims.tenant.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class OrderIdempotencyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testOrderIdempotency() throws Exception {
        String token = getAdminToken();
        Long tenantId = getTenantId();

        OrderRequest request = new OrderRequest();
        request.setSupplierId(1L); // Assuming supplier 1 exists
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(1L); // Assuming product 1 exists
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

        Order firstOrder = objectMapper.readValue(firstResponse, Order.class);

        // Second request with same key
        String secondResponse = mockMvc.perform(post("/api/v1/tenant/orders/purchase")
                .header("X-Tenant-ID", tenantId)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()) // Service returns existing order, which is fine
                .andReturn().getResponse().getContentAsString();

        Order secondOrder = objectMapper.readValue(secondResponse, Order.class);

        assertEquals(firstOrder.getId(), secondOrder.getId(), "Should return the same order ID");

        assertTrue(orderRepository.findByIdempotencyKey(idempotencyKey).isPresent());
    }

    private Long getTenantId() {
        return testTenant1Id;
    }

    private String getAdminToken() throws Exception {
        // Root admin can act on behalf of any tenant if X-Tenant-ID is set
        return login("root@ims.com", TEST_ROOT_PASSWORD, "PLATFORM", systemTenantId);
    }
}
