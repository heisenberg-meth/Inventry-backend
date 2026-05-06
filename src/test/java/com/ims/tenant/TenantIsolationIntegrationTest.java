package com.ims.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.BaseIntegrationTest;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.response.LoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
class TenantIsolationIntegrationTest extends BaseIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        private String tenant1Token;
        private String tenant2Token;
        private Long tenant1Id;
        private Long tenant2Id;

        @BeforeEach
        void setup() throws Exception {
                cleanupDatabase();
                mockRedisAndCache();

                // Get tenant IDs from seeded data (set in BaseIntegrationTest.seedTestData)
                tenant1Id = testTenant1Id;
                tenant2Id = testTenant2Id;

                // Create users directly in DB for each tenant
                String passwordHash = passwordEncoder.encode("password123");
                String t1Email = "tenant1-" + UUID.randomUUID() + "@test.com";
                String t2Email = "tenant2-" + UUID.randomUUID() + "@test.com";

                // User for tenant 1
                jdbcTemplate.update(
                                "INSERT INTO users (name, email, password_hash, role, scope, tenant_id, is_active, is_verified, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                                "Tenant1 User", t1Email, passwordHash, "ADMIN", "TENANT", tenant1Id, true, true);

                // User for tenant 2
                jdbcTemplate.update(
                                "INSERT INTO users (name, email, password_hash, role, scope, tenant_id, is_active, is_verified, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                                "Tenant2 User", t2Email, passwordHash, "ADMIN", "TENANT", tenant2Id, true, true);

                // Verify users (already done via INSERT above by setting is_verified=true)

                // Login tenant 1
                tenant1Token = loginAndGetToken(t1Email, "password123", "T1001");

                // Login tenant 2
                tenant2Token = loginAndGetToken(t2Email, "password123", "T2001");
        }

        private String loginAndGetToken(String email, String password, String companyCode) throws Exception {
                LoginRequest loginRequest = new LoginRequest();
                loginRequest.setEmail(email);
                loginRequest.setPassword(password);
                loginRequest.setCompanyCode(companyCode);

                MockHttpServletRequestBuilder loginBuilder = post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest));
                MvcResult loginResult = mockMvc.perform(loginBuilder)
                                .andExpect(status().isOk())
                                .andReturn();

                LoginResponse response = objectMapper.readValue(
                                loginResult.getResponse().getContentAsString(), LoginResponse.class);
                return response.getAccessToken();
        }

        @Test
        void tenant1CannotAccessTenant2Data() throws Exception {
                // Create a product in tenant1
                String productPayload = "{\n" +
                                "\"name\": \"Tenant1 Product\",\n" +
                                "\"sku\": \"T1-SKU-" + UUID.randomUUID().toString().substring(0, 8) + "\",\n" +
                                "\"purchasePrice\": 5.99,\n" +
                                "\"salePrice\": 10.99,\n" +
                                "\"quantity\": 100\n" +
                                "}";

                MockHttpServletRequestBuilder createProductBuilder = post("/api/tenant/products")
                                .header("Authorization", "Bearer " + tenant1Token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(productPayload);
                mockMvc.perform(createProductBuilder)
                                .andExpect(status().isCreated());

                // Try to access product from tenant2 context
                MockHttpServletRequestBuilder getProductsBuilder = get("/api/tenant/products")
                                .header("Authorization", "Bearer " + tenant2Token);
                mockMvc.perform(getProductsBuilder)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isEmpty());
        }

        @Test
        void tenantContextIsIsolated() throws Exception {
                // Verify tenant context is set correctly for each tenant
                MockHttpServletRequestBuilder getTenant1Builder = get("/api/tenant/settings")
                                .header("Authorization", "Bearer " + tenant1Token);
                mockMvc.perform(getTenant1Builder)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.workspace_slug").value("t1"));

                MockHttpServletRequestBuilder getTenant2Builder = get("/api/tenant/settings")
                                .header("Authorization", "Bearer " + tenant2Token);
                mockMvc.perform(getTenant2Builder)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.workspace_slug").value("t2"));
        }
}
