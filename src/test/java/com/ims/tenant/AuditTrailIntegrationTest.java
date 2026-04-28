package com.ims.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ims.BaseIntegrationTest;
import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.ProductResponse;
import com.ims.shared.auth.SignupService;
import java.math.BigDecimal;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration",
        "spring.cache.type=none"
})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test-no-security")
public class AuditTrailIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private SignupService signupService;

    @BeforeEach
    void setup() {
        cleanupDatabase();
        mockRedisAndCache();
    }

    @Test
    void testProductAuditLogging() throws Exception {
        SignupRequest signup = new SignupRequest();
        signup.setBusinessName("Audit Corp");
        signup.setWorkspaceSlug("audit-corp");
        signup.setBusinessType("RETAIL");
        signup.setOwnerName("Admin");
        signup.setOwnerEmail("admin@audit.com");
        signup.setPassword("password123");
        com.ims.dto.response.SignupResponse response = signupService.signup(Objects.requireNonNull(signup));
        verifyUserEmail("admin@audit.com");
        verifyUser("admin@audit.com");

        Long tenantId = Objects
                .requireNonNull(tenantRepository.findByWorkspaceSlug("audit-corp").orElseThrow().getId());
        String token = login("admin@audit.com", "password123", Objects.requireNonNull(response.getCompanyCode()),
                tenantId);

        // 1. Create Product
        CreateProductRequest createReq = new CreateProductRequest();
        createReq.setName("Audit Product");
        createReq.setSku("AUDIT-001");
        createReq.setSalePrice(new BigDecimal("10.00"));

        String requestJson = objectMapper.writeValueAsString(createReq);
        MvcResult result = mockMvc
                .perform(
                        post("/api/v1/tenant/products")
                                .header("Authorization", "Bearer " + token)
                                .with(tenant(Objects.requireNonNull(String.valueOf(tenantId))))
                                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                                .content(Objects.requireNonNull(requestJson)))
                .andExpect(status().isCreated())
                .andReturn();

        ProductResponse product = objectMapper.readValue(result.getResponse().getContentAsString(),
                ProductResponse.class);

        // 2. Verify Audit Log for creation
        mockMvc
                .perform(
                        get("/api/v1/tenant/audit-logs")
                                .header("Authorization", "Bearer " + token)
                                .with(tenant(Objects.requireNonNull(String.valueOf(tenantId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.action == 'CREATE')]").exists());

        // 3. Update Product
        createReq.setName("Updated Audit Product");
        mockMvc
                .perform(
                        put("/api/v1/tenant/products/" + product.getId())
                                .header("Authorization", "Bearer " + token)
                                .with(tenant(Objects.requireNonNull(String.valueOf(tenantId))))
                                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                                .content(Objects.requireNonNull(objectMapper.writeValueAsString(createReq))))
                .andExpect(status().isOk());

        // 4. Verify Audit Log for update
        mockMvc
                .perform(
                        get("/api/v1/tenant/audit-logs")
                                .header("Authorization", "Bearer " + token)
                                .with(tenant(Objects.requireNonNull(String.valueOf(tenantId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.action == 'UPDATE')]").exists());
    }

    @Test
    void testAuditIsolation() throws Exception {
        // Tenant 1
        com.ims.dto.response.SignupResponse r1 = signupService.signup(
                Objects.requireNonNull(createSignupRequest("Audit Corp 1", "audit-corp-1", "admin1@audit.com")));
        verifyUserEmail("admin1@audit.com");
        verifyUser("admin1@audit.com");
        Long t1Id = Objects.requireNonNull(tenantRepository.findByWorkspaceSlug("audit-corp-1").orElseThrow().getId());
        String t1Token = login("admin1@audit.com", "password123", Objects.requireNonNull(r1.getCompanyCode()), t1Id);

        // Tenant 2
        com.ims.dto.response.SignupResponse r2 = signupService.signup(
                Objects.requireNonNull(createSignupRequest("Audit Corp 2", "audit-corp-2", "admin2@audit.com")));
        verifyUserEmail("admin2@audit.com");
        verifyUser("admin2@audit.com");
        Long t2Id = Objects.requireNonNull(tenantRepository.findByWorkspaceSlug("audit-corp-2").orElseThrow().getId());
        String t2Token = login("admin2@audit.com", "password123", Objects.requireNonNull(r2.getCompanyCode()), t2Id);

        // T1 performs an action
        CreateProductRequest createReq = new CreateProductRequest();
        createReq.setName("T1 Product");
        createReq.setSku("T1-001");
        createReq.setSalePrice(new BigDecimal("10.00"));

        mockMvc
                .perform(
                        post("/api/v1/tenant/products")
                                .header("Authorization", "Bearer " + t1Token)
                                .with(tenant(Objects.requireNonNull(String.valueOf(t1Id))))
                                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                                .content(Objects.requireNonNull(objectMapper.writeValueAsString(createReq))))
                .andExpect(status().isCreated());

        // T1 should see 4 logs (Signup + Login + Create + Category Create)
        mockMvc
                .perform(
                        get("/api/v1/tenant/audit-logs")
                                .header("Authorization", "Bearer " + t1Token)
                                .with(tenant(Objects.requireNonNull(String.valueOf(t2Id)))))
                .andExpect(status().isForbidden());

        // T2 should see 2 logs (Signup + Login)
        mockMvc
                .perform(
                        get("/api/v1/tenant/audit-logs")
                                .header("Authorization", "Bearer " + t2Token)
                                .with(tenant(Objects.requireNonNull(String.valueOf(t2Id)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    private SignupRequest createSignupRequest(@NonNull String name, @NonNull String workspaceSlug,
            @NonNull String email) {
        SignupRequest signup = new SignupRequest();
        signup.setBusinessName(Objects.requireNonNull(name));
        signup.setWorkspaceSlug(Objects.requireNonNull(workspaceSlug));
        signup.setBusinessType("RETAIL");
        signup.setOwnerName("Admin");
        signup.setOwnerEmail(Objects.requireNonNull(email));
        signup.setPassword("password123");
        return signup;
    }
}
