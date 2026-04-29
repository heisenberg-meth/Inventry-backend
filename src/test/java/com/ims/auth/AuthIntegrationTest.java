package com.ims.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.ims.BaseIntegrationTest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.SignupResponse;
import com.ims.shared.auth.SignupService;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.lang.NonNull;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test-no-security")
public class AuthIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private SignupService signupService;

    @BeforeEach
    void setup() {
        cleanupDatabase();
        mockRedisAndCache();
    }

    @Test
    void testSecurityAndIsolationFlow() throws Exception {
        // 1. Signup Tenant 1
        String uniqueId1 = UUID.randomUUID().toString().substring(0, 8);
        SignupRequest t1Signup = createSignupRequest(
                "Tenant 1 " + uniqueId1, "t1-auth-" + uniqueId1, "admin1-" + uniqueId1 + "@t1.com");
        SignupResponse t1Response = signupService.signup(t1Signup);

        // 2. Signup Tenant 2
        String uniqueId2 = UUID.randomUUID().toString().substring(0, 8);
        SignupRequest t2Signup = createSignupRequest(
                "Tenant 2 " + uniqueId2, "t2-auth-" + uniqueId2, "admin2-" + uniqueId2 + "@t2.com");
        SignupResponse t2Response = signupService.signup(t2Signup);

        // 3. Verify users (simulating realistic email verification flow)
        verifyUserEmail("admin1-" + uniqueId1 + "@t1.com");
        verifyUserEmail("admin2-" + uniqueId2 + "@t2.com");

        // Get IDs
        Long t1Id = Objects
                .requireNonNull(tenantRepository.findByWorkspaceSlug("t1-auth-" + uniqueId1).orElseThrow().getId());
        Long t2Id = Objects
                .requireNonNull(tenantRepository.findByWorkspaceSlug("t2-auth-" + uniqueId2).orElseThrow().getId());

        // 4. Login Tenant 1
        String t1Token = login("admin1-" + uniqueId1 + "@t1.com", "password123",
                Objects.requireNonNull(t1Response.getCompanyCode()), t1Id);

        // 5. Verify Tenant 1 Isolation (Should only see 1 user: admin1)
        mockMvc
                .perform(
                        get("/api/v1/tenant/users")
                                .header("Authorization", "Bearer " + t1Token)
                                .with(tenant(Objects.requireNonNull(t1Id.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].email").value("admin1-" + uniqueId1 + "@t1.com"));

        // 6. Login Tenant 2
        String t2Token = login("admin2-" + uniqueId2 + "@t2.com", "password123",
                Objects.requireNonNull(t2Response.getCompanyCode()), t2Id);

        // 7. Verify Tenant 2 Isolation (Should only see 1 user: admin2)
        mockMvc
                .perform(
                        get("/api/v1/tenant/users")
                                .header("Authorization", "Bearer " + t2Token)
                                .with(tenant(Objects.requireNonNull(t2Id.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].email").value("admin2-" + uniqueId2 + "@t2.com"));

        // 8. Verify Logout and Blacklisting
        mockMvc
                .perform(
                        post("/api/v1/auth/logout")
                                .header("Authorization", "Bearer " + t1Token)
                                .with(tenant(Objects.requireNonNull(t1Id.toString()))))
                .andExpect(status().isOk());

        // Mock Redis blacklist check for next request
        doReturn(true).when(redisTemplate).hasKey(Objects.requireNonNull(anyString()));

        mockMvc
                .perform(
                        get("/api/v1/tenant/users")
                                .header("Authorization", "Bearer " + t1Token)
                                .with(tenant(Objects.requireNonNull(t1Id.toString()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testUnauthorizedAccess() throws Exception {
        mockMvc
                .perform(get("/api/v1/tenant/users").with(tenant("1")))
                .andExpect(status().isUnauthorized());
    }

    @NonNull
    private SignupRequest createSignupRequest(@NonNull String name, @NonNull String workspaceSlug,
            @NonNull String email) {
        SignupRequest req = new SignupRequest();
        req.setBusinessName(Objects.requireNonNull(name));
        req.setBusinessType("Retail");
        req.setWorkspaceSlug(Objects.requireNonNull(workspaceSlug));
        req.setOwnerName(Objects.requireNonNull("Owner " + name));
        req.setOwnerEmail(Objects.requireNonNull(email));
        req.setPassword("password123");
        return req;
    }
}
