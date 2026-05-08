package com.ims.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.ims.BaseIntegrationTest;
import com.ims.TestDataFactory;
import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.ProductResponse;
import com.ims.shared.auth.SignupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc

@org.springframework.security.test.context.support.WithMockUser(username = "admin", authorities = { "ADMIN",
                "ROLE_ADMIN", "create_product", "view_product", "update_product", "delete_product", "create_order",
                "view_order", "create_supplier", "view_supplier", "delete_supplier", "manage_stock", "view_stock" })
public class AuditTrailIntegrationTest extends BaseIntegrationTest {

        @Autowired
        private SignupService signupService;

        @BeforeEach
        void setup() {
                cleanupDatabase();
        }

        @Test
        void testProductAuditLogging() throws Exception {
                String uniqueEmail = TestDataFactory.email();
                SignupRequest signup = new SignupRequest();
                signup.setBusinessName(TestDataFactory.business());
                signup.setBusinessType("RETAIL");
                signup.setOwnerName("Admin");
                signup.setOwnerEmail(uniqueEmail);
                signup.setPassword("password123");
                com.ims.dto.response.SignupResponse response = signupService.signup(signup);
                verifyUser(uniqueEmail);

                String token = login(uniqueEmail, "password123", response.getCompanyCode());

                // 1. Create Product
                CreateProductRequest createReq = new CreateProductRequest();
                createReq.setName("Audit Product");
                createReq.setSku("AUDIT-001");
                createReq.setSalePrice(new BigDecimal("10.00"));

                String requestJson = objectMapper.writeValueAsString(createReq);
                MvcResult result = mockMvc.perform(post("/api/v1/tenant/products")
                                .header("Authorization", "Bearer " + token)
                                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                                .content(Objects.requireNonNull(requestJson)))
                                .andExpect(status().isCreated())
                                .andReturn();

                ProductResponse product = objectMapper.readValue(result.getResponse().getContentAsString(),
                                ProductResponse.class);

                // 2. Verify Audit Log for creation - endpoint requires ADMIN role
                // For now, skip audit verification as signed-up users have USER role
                mockMvc.perform(get("/api/tenant/audits")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content[?(@.action == 'CREATE')]").exists());

                // 3. Update Product
                createReq.setName("Updated Audit Product");
                String updateJson = objectMapper.writeValueAsString(createReq);
                mockMvc.perform(put("/api/v1/tenant/products/" + product.getId())
                                .header("Authorization", "Bearer " + token)
                                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                                .content(Objects.requireNonNull(updateJson)))
                                .andExpect(status().isOk());

                // 4. Verify Audit Log for update - endpoint requires ADMIN role
                // For now, skip audit verification
                mockMvc.perform(get("/api/tenant/audits")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content[?(@.action == 'UPDATE')]").exists());
        }

        @Test
        void testAuditIsolation() throws Exception {
                // Tenant 1
                String email1 = TestDataFactory.email();
                String slug1 = TestDataFactory.slug();
                com.ims.dto.response.SignupResponse r1 = signupService
                                .signup(createSignupRequest(TestDataFactory.business(), slug1, email1));
                verifyUser(email1);
                String t1Token = login(email1, "password123", r1.getCompanyCode());

                // Tenant 2
                String email2 = TestDataFactory.email();
                String slug2 = TestDataFactory.slug();
                com.ims.dto.response.SignupResponse r2 = signupService
                                .signup(createSignupRequest(TestDataFactory.business(), slug2, email2));
                verifyUser(email2);
                String t2Token = login(email2, "password123", r2.getCompanyCode());

                // T1 performs an action
                CreateProductRequest createReq = new CreateProductRequest();
                createReq.setName("T1 Product");
                createReq.setSku("T1-001");
                createReq.setSalePrice(new BigDecimal("10.00"));
                String t1ReqJson = objectMapper.writeValueAsString(createReq);
                mockMvc.perform(post("/api/v1/tenant/products")
                                .header("Authorization", "Bearer " + t1Token)
                                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                                .content(Objects.requireNonNull(t1ReqJson)))
                                .andExpect(status().isCreated());

                // T1 should see 4 logs (Category Create + Signup + Login + Product Create)
                mockMvc.perform(get("/api/tenant/audits")
                                .header("Authorization", "Bearer " + t1Token)
                                .header("X-Tenant-ID", r1.getTenantId().toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content.length()").value(4));

                // T2 should see 3 logs (Category Create + Signup + Login)
                mockMvc.perform(get("/api/tenant/audits")
                                .header("Authorization", "Bearer " + t2Token)
                                .header("X-Tenant-ID", r2.getTenantId().toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content.length()").value(3));
        }

}