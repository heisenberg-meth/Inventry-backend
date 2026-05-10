package com.ims.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.BaseIntegrationTest;
import com.ims.order.dto.CreateOrderRequest;
import com.ims.order.dto.OrderItemRequest;
import com.ims.order.dto.OrderResponse;
import com.ims.order.entity.OrderType;
import com.ims.product.Product;
import com.ims.product.ProductRepository;
import com.ims.tenant.service.InventoryService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
public class OrderIntegrationTest extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ProductRepository productRepository;

  @Autowired private InventoryService inventoryService;

  @Autowired private ObjectMapper objectMapper;

  @Test
  @WithMockUser(roles = "ADMIN")
  void testCreateSaleOrder() throws Exception {
    // 1. Setup product
    Product product =
        Product.builder()
            .tenantId(testTenant1Id)
            .name("Test Product 1")
            .sku("TEST-SKU-1")
            .salePrice(new BigDecimal("100.00"))
            .build();
    product = productRepository.save(product);

    // 2. Add stock
    inventoryService.increaseStock(testTenant1Id, product.getId(), 10, "Initial stock", testUserId);

    // 3. Create a sale order
    CreateOrderRequest request =
        CreateOrderRequest.builder()
            .type(OrderType.SALE)
            .items(
                List.of(OrderItemRequest.builder().productId(product.getId()).quantity(2).build()))
            .build();

    String responseJson =
        mockMvc
            .perform(
                post("/api/v1/tenant/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .header("X-Tenant-Id", testTenant1Id.toString()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    OrderResponse response = objectMapper.readValue(responseJson, OrderResponse.class);

    // Stock should NOT be deducted yet (PENDING state)
    assertThat(inventoryService.getAvailableStock(testTenant1Id, product.getId())).isEqualTo(10);

    // Confirm the order to trigger stock deduction
    mockMvc
        .perform(
            post("/api/v1/tenant/orders/" + response.getId() + "/confirm")
                .header("X-Tenant-Id", testTenant1Id.toString()))
        .andExpect(status().isOk());

    // 4. Verify response
    assertThat(response.getId()).isNotNull();
    assertThat(response.getType()).isEqualTo(OrderType.SALE);

    // 5. Verify stock deduction
    int afterStock = inventoryService.getAvailableStock(testTenant1Id, product.getId());
    assertThat(afterStock).isEqualTo(8);
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void testCreateSaleOrderInsufficientStock() throws Exception {
    // 1. Setup product with low stock
    Product product =
        Product.builder()
            .tenantId(testTenant1Id)
            .name("Test Product 3")
            .sku("TEST-SKU-3")
            .salePrice(new BigDecimal("100.00"))
            .build();
    product = productRepository.save(product);
    inventoryService.increaseStock(testTenant1Id, product.getId(), 5, "Initial stock", testUserId);

    // 2. Attempt to buy 10 (only 5 in stock)
    CreateOrderRequest request =
        CreateOrderRequest.builder()
            .type(OrderType.SALE)
            .items(
                List.of(OrderItemRequest.builder().productId(product.getId()).quantity(10).build()))
            .build();

    mockMvc
        .perform(
            post("/api/v1/tenant/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Tenant-Id", testTenant1Id.toString()))
        .andExpect(status().isUnprocessableEntity());
  }
}
