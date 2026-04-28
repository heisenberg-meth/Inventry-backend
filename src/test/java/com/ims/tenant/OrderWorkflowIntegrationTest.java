package com.ims.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ims.BaseIntegrationTest;
import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.ProductResponse;
import com.ims.dto.response.SignupResponse;
import com.ims.model.Customer;
import com.ims.shared.auth.SignupService;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.service.CustomerService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration",
                "spring.cache.type=none"
})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test-no-security")
public class OrderWorkflowIntegrationTest extends BaseIntegrationTest {

        @Autowired
        private SignupService signupService;
        @Autowired
        private CustomerService customerService;

        @BeforeEach
        void setup() {
                cleanupDatabase();
                mockRedisAndCache();
        }

        @Test
        void testCompleteOrderWorkflow() throws Exception {
                // 1. Setup Tenant and Data
                SignupRequest signup = new SignupRequest();
                signup.setBusinessName("Order Corp");
                signup.setBusinessType("RETAIL");
                signup.setOwnerName("Admin");
                signup.setOwnerEmail("admin@order.com");
                signup.setPassword("password123");
                SignupResponse response = signupService.signup(signup);
                verifyUserEmail("admin@order.com");
                verifyUser("admin@order.com");

                Long tenantId = Objects.requireNonNull(tenantRepository
                                .findByWorkspaceSlug(Objects.requireNonNull(response.getWorkspaceSlug())).orElseThrow()
                                .getId());
                String token = login("admin@order.com", "password123",
                                Objects.requireNonNull(response.getCompanyCode()), tenantId);

                Customer customer;
                try {
                        TenantContext.setTenantId(tenantId);
                        com.ims.dto.request.CustomerRequest custReq = new com.ims.dto.request.CustomerRequest();
                        custReq.setName("Test Customer");
                        com.ims.dto.response.CustomerResponse custResponse = customerService.create(custReq);
                        customer = Customer.builder().id(Objects.requireNonNull(custResponse.getId()))
                                        .name(Objects.requireNonNull(custResponse.getName())).build();
                } finally {
                        TenantContext.clear();
                }

                CreateProductRequest createReq = new CreateProductRequest();
                createReq.setName("Test Product");
                createReq.setSku("PROD-001");
                createReq.setSalePrice(new BigDecimal("100.00"));
                MvcResult prodResult = mockMvc
                                .perform(
                                                post("/api/v1/tenant/products")
                                                                .header("Authorization", "Bearer " + token)
                                                                .with(tenant(Objects.requireNonNull(
                                                                                String.valueOf(tenantId))))
                                                                .contentType(Objects.requireNonNull(
                                                                                MediaType.APPLICATION_JSON))
                                                                .content(Objects.requireNonNull(objectMapper
                                                                                .writeValueAsString(createReq))))
                                .andExpect(status().isCreated())
                                .andReturn();
                ProductResponse product = objectMapper.readValue(
                                prodResult.getResponse().getContentAsString(), ProductResponse.class);

                // 2. Stock In (100 units)
                mockMvc
                                .perform(
                                                post("/api/v1/tenant/stock/in")
                                                                .header("Authorization", "Bearer " + token)
                                                                .with(tenant(Objects.requireNonNull(
                                                                                String.valueOf(tenantId))))
                                                                .contentType(Objects.requireNonNull(
                                                                                MediaType.APPLICATION_JSON))
                                                                .content(
                                                                                Objects.requireNonNull(
                                                                                                objectMapper.writeValueAsString(
                                                                                                                Map.of(
                                                                                                                                "product_id",
                                                                                                                                product.getId(),
                                                                                                                                "quantity",
                                                                                                                                100,
                                                                                                                                "notes",
                                                                                                                                "Initial Stock")))))
                                .andExpect(status().isOk());

                verifyStock(token, product.getId(), 100, tenantId);

                // 3. Create Sales Order (Status -> PENDING)
                Map<String, Object> orderReq = Map.of(
                                "customer_id", customer.getId(),
                                "items",
                                List.of(
                                                Map.of("product_id", product.getId(), "quantity", 10, "unit_price",
                                                                100.00)));

                MvcResult orderResult = mockMvc
                                .perform(
                                                post("/api/v1/tenant/orders/sale")
                                                                .header("Authorization", "Bearer " + token)
                                                                .with(tenant(Objects.requireNonNull(
                                                                                String.valueOf(tenantId))))
                                                                .contentType(Objects.requireNonNull(
                                                                                MediaType.APPLICATION_JSON))
                                                                .content(Objects.requireNonNull(objectMapper
                                                                                .writeValueAsString(orderReq))))
                                .andExpect(status().isCreated())
                                .andReturn();

                Map<String, Object> orderResponse = objectMapper.readValue(
                                orderResult.getResponse().getContentAsString(),
                                new TypeReference<Map<String, Object>>() {
                                });
                Long orderId = Long.valueOf(orderResponse.get("id").toString());

                // 4. Confirm Order (Status -> CONFIRMED, stock 100 -> 90)
                mockMvc
                                .perform(
                                                post("/api/v1/tenant/orders/" + orderId + "/confirm")
                                                                .header("Authorization", "Bearer " + token)
                                                                .with(tenant(Objects.requireNonNull(
                                                                                String.valueOf(tenantId)))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("CONFIRMED"));

                verifyStock(token, product.getId(), 90, tenantId);

                // 5. Cancel Order (Status -> CANCELLED, stock 90 -> 100)
                mockMvc
                                .perform(
                                                post("/api/v1/tenant/orders/" + orderId + "/cancel")
                                                                .header("Authorization", "Bearer " + token)
                                                                .with(tenant(Objects.requireNonNull(
                                                                                String.valueOf(tenantId)))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("CANCELLED"));

                verifyStock(token, product.getId(), 100, tenantId);
        }

        private void verifyStock(String token, Long productId, int expected, Long tenantId)
                        throws Exception {
                mockMvc
                                .perform(
                                                get("/api/v1/tenant/products/" + productId)
                                                                .header("Authorization", "Bearer " + token)
                                                                .with(tenant(Objects.requireNonNull(
                                                                                String.valueOf(tenantId)))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.stock").value(expected));
        }
}
