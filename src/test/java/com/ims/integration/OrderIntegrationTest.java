package com.ims.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.BaseIntegrationTest;
import com.ims.product.Product;
import com.ims.order.dto.CreateOrderRequest;
import com.ims.order.dto.OrderItemRequest;
import com.ims.order.dto.OrderResponse;
import com.ims.order.entity.OrderType;
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

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ProductRepository productRepository;

        @Autowired
        private com.ims.tenant.repository.SupplierRepository supplierRepository;

        @Autowired
        private InventoryService inventoryService;

        @Autowired
        private ObjectMapper objectMapper;

        @org.junit.jupiter.api.BeforeEach
        void setUpTenant() {
                com.ims.shared.auth.TenantContext.setTenantId(1L);
        }

        @Test
        @WithMockUser(authorities = { "ROLE_ADMIN", "create_order", "view_product" })
        public void testCreateSaleOrder() throws Exception {
                // 1. Create a product
                Product product = Product.builder()
                                .tenantId(1L)
                                .name("Test Product")
                                .sku("TEST-SKU-1")
                                .salePrice(new BigDecimal("100.00"))
                                .build();
                product = productRepository.save(product);

                // 2. Add stock
                inventoryService.increaseStock(1L, product.getId(), 10, "Initial stock", 1L);

                // 3. Create a sale order
                CreateOrderRequest request = CreateOrderRequest.builder()
                                .type(OrderType.SALE)
                                .items(List.of(OrderItemRequest.builder()
                                                .productId(product.getId())
                                                .quantity(2)
                                                .build()))
                                .build();

                String responseJson = mockMvc.perform(post("/api/v1/tenant/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("X-Tenant-Id", "1"))
                                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                                .andExpect(status().isCreated())                                .andReturn().getResponse().getContentAsString();

                OrderResponse response = objectMapper.readValue(responseJson, OrderResponse.class);

                // Stock should NOT be deducted yet (PENDING state)
                assertThat(inventoryService.getAvailableStock(1L, product.getId())).isEqualTo(10);

                // Confirm the order to trigger stock deduction
                mockMvc.perform(post("/api/v1/tenant/orders/" + response.getId() + "/confirm")
                                .header("X-Tenant-Id", "1"))
                                .andExpect(status().isOk());

                // 4. Verify response
                assertThat(response.getId()).isNotNull();
                assertThat(response.getType()).isEqualTo(OrderType.SALE);
                assertThat(response.getTotalAmount().compareTo(new BigDecimal("200.00"))).isEqualTo(0);
                assertThat(response.getItems()).hasSize(1);
                assertThat(response.getItems().get(0).getQuantity()).isEqualTo(2);

                // 5. Verify stock deduction
                Integer availableStock = inventoryService.getAvailableStock(1L, product.getId());
                assertThat(availableStock).isEqualTo(8);
        }

        @Test
        @WithMockUser(authorities = { "ROLE_ADMIN", "create_order", "view_product" })
        public void testCreatePurchaseOrder() throws Exception {
                // 1. Create a supplier
                com.ims.model.Supplier supplier = com.ims.model.Supplier.builder()
                                .tenantId(1L)
                                .name("Test Supplier")
                                .email("supplier@test.com")
                                .build();
                supplier = supplierRepository.save(supplier);

                // 2. Create a product
                Product product = Product.builder()
                                .tenantId(1L)
                                .name("Test Product 2")
                                .sku("TEST-SKU-2")
                                .purchasePrice(new BigDecimal("50.00"))
                                .salePrice(new BigDecimal("100.00"))
                                .build();
                product = productRepository.save(product);

                // 3. Create a purchase order
                CreateOrderRequest request = CreateOrderRequest.builder()
                                .type(OrderType.PURCHASE)
                                .supplierId(supplier.getId())
                                .items(List.of(OrderItemRequest.builder()
                                                .productId(product.getId())
                                                .quantity(5)
                                                .build()))
                                .build();

                String responseJson = mockMvc.perform(post("/api/v1/tenant/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("X-Tenant-Id", "1"))
                                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                                .andExpect(status().isCreated())                                .andReturn().getResponse().getContentAsString();

                OrderResponse response = objectMapper.readValue(responseJson, OrderResponse.class);

                // Stock should NOT be increased yet
                assertThat(inventoryService.getAvailableStock(1L, product.getId())).isEqualTo(0);

                // Complete the order to trigger stock increase
                mockMvc.perform(post("/api/v1/tenant/orders/" + response.getId() + "/complete")
                                .header("X-Tenant-Id", "1"))
                                .andExpect(status().isOk());

                // 3. Verify stock increase
                Integer availableStock = inventoryService.getAvailableStock(1L, product.getId());
                assertThat(availableStock).isEqualTo(5);
        }

        @Test
        @WithMockUser(authorities = { "ROLE_ADMIN", "create_order", "view_product" })
        public void testCreateSaleOrderInsufficientStock() throws Exception {
                // 1. Create a product with 1 stock
                Product product = Product.builder()
                                .tenantId(1L)
                                .name("Test Product 3")
                                .sku("TEST-SKU-3")
                                .salePrice(new BigDecimal("100.00"))
                                .build();
                product = productRepository.save(product);
                inventoryService.increaseStock(1L, product.getId(), 1, "Initial stock", 1L);

                // 2. Attempt to buy 2
                CreateOrderRequest request = CreateOrderRequest.builder()
                                .type(OrderType.SALE)
                                .items(List.of(OrderItemRequest.builder()
                                                .productId(product.getId())
                                                .quantity(2)
                                                .build()))
                                .build();

                mockMvc.perform(post("/api/v1/tenant/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("X-Tenant-Id", "1"))
                                .andExpect(status().isUnprocessableEntity());
        }
}
