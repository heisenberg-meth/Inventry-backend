package com.ims.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.BaseIntegrationTest;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.response.LoginResponse;
import com.ims.model.Tenant;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
@org.springframework.security.test.context.support.WithMockUser(username = "admin", authorities = {
        "ADMIN",
        "ROLE_ADMIN",
        "create_product",
        "view_product",
        "update_product",
        "delete_product",
        "create_order",
        "view_order",
        "create_supplier",
        "view_supplier",
        "delete_supplier",
        "manage_stock",
        "view_stock"
})
class TenantIsolationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String tenant1Token;
    private String tenant2Token;
    private Tenant tenant1;
    private Tenant tenant2;

    @BeforeEach
    void setup() throws Exception {
        cleanupDatabase();

        // Create fresh tenants and users using factory
        tenant1 = testDataFactory.createTenant();
        tenant2 = testDataFactory.createTenant();

        var user1 = testDataFactory.createUser(tenant1);
        var user2 = testDataFactory.createUser(tenant2);

        // Login each user
        tenant1Token = loginAndGetToken(user1.getEmail(), "password123", tenant1.getCompanyCode());
        tenant2Token = loginAndGetToken(user2.getEmail(), "password123", tenant2.getCompanyCode());
    }

    private String loginAndGetToken(String email, String password, String companyCode)
            throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(email);
        loginRequest.setPassword(password);
        loginRequest.setCompanyCode(companyCode);

        MockHttpServletRequestBuilder loginBuilder = post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest));
        MvcResult loginResult = mockMvc.perform(loginBuilder).andExpect(status().isOk()).andReturn();

        LoginResponse response = objectMapper.readValue(loginResult.getResponse().getContentAsString(),
                LoginResponse.class);
        return response.getAccessToken();
    }

    @Test
    void tenant1CannotAccessTenant2Data() throws Exception {
        // Create a product in tenant1
        String productPayload = "{\n"
                + "\"name\": \"Tenant1 Product\",\n"
                + "\"sku\": \"T1-SKU-"
                + UUID.randomUUID().toString().substring(0, 8)
                + "\",\n"
                + "\"purchasePrice\": 5.99,\n"
                + "\"salePrice\": 10.99,\n"
                + "\"quantity\": 100\n"
                + "}";

        MockHttpServletRequestBuilder createProductBuilder = post("/api/v1/tenant/products")
                .header("Authorization", "Bearer " + tenant1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(productPayload);
        mockMvc.perform(createProductBuilder).andExpect(status().isCreated());

        // Try to access product from tenant2 context
        MockHttpServletRequestBuilder getProductsBuilder = get("/api/v1/tenant/products").header("Authorization",
                "Bearer " + tenant2Token);
        mockMvc
                .perform(getProductsBuilder)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void tenantContextIsIsolated() throws Exception {
        // Verify tenant context is set correctly for each tenant
        MockHttpServletRequestBuilder getTenant1Builder = get("/api/v1/tenant/settings").header("Authorization",
                "Bearer " + tenant1Token);
        mockMvc
                .perform(getTenant1Builder)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspace_slug").value(tenant1.getWorkspaceSlug()));

        MockHttpServletRequestBuilder getTenant2Builder = get("/api/v1/tenant/settings").header("Authorization",
                "Bearer " + tenant2Token);
        mockMvc
                .perform(getTenant2Builder)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspace_slug").value(tenant2.getWorkspaceSlug()));
    }
}
