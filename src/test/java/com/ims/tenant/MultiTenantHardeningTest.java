package com.ims.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ims.BaseIntegrationTest;
import com.ims.dto.request.SignupRequest;
import com.ims.model.User;
import com.ims.shared.auth.SignupService;
import com.ims.shared.auth.TenantContext;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class MultiTenantHardeningTest extends BaseIntegrationTest {

        @Autowired
        private SignupService signupService;

        @Override
        @BeforeEach
        protected void setUp() throws Exception {
                super.setUp();
                cleanupDatabase();
        }

        @Test
        @DisplayName("Database-level tenant isolation works via Hibernate filters")
        void testDatabaseLevelTenantIsolation() {
                // 1. Create Tenant A
                String uniqueIdA = UUID.randomUUID().toString().substring(0, 8);
                SignupRequest reqA = createSignupRequest("Tenant A " + uniqueIdA, "ta-" + uniqueIdA,
                                "adminA-" + uniqueIdA + "@ta.com");
                signupService.signup(reqA);
                Long tenantAId = tenantRepository.findByWorkspaceSlug("ta-" + uniqueIdA).orElseThrow().getId();

                // 2. Create Tenant B
                String uniqueIdB = UUID.randomUUID().toString().substring(0, 8);
                SignupRequest reqB = createSignupRequest("Tenant B " + uniqueIdB, "tb-" + uniqueIdB,
                                "adminB-" + uniqueIdB + "@tb.com");
                signupService.signup(reqB);
                Long tenantBId = tenantRepository.findByWorkspaceSlug("tb-" + uniqueIdB).orElseThrow().getId();

                // Get User B ID
                TenantContext.setTenantId(tenantBId);
                User userB = userRepository.findByEmailGlobal("adminB-" + uniqueIdB + "@tb.com").orElseThrow();
                Long userBId = userB.getId();
                TenantContext.clear();

                // 3. Test Isolation - Tenant A trying to access Tenant B's User
                TenantContext.setTenantId(tenantAId);

                // Should NOT find Tenant B's user because Hibernate filter intercepts it
                assertThat(userRepository.findById(userBId)).isEmpty();

                TenantContext.clear();
        }

        @Test
        @DisplayName("REST API requests correctly scope data to the current tenant")
        void testRestApiTenantIsolation() throws Exception {
                String uniqueIdA = UUID.randomUUID().toString().substring(0, 8);
                SignupRequest reqA = createSignupRequest("Tenant A " + uniqueIdA, "ta-" + uniqueIdA,
                                "adminA-" + uniqueIdA + "@ta.com");
                var resA = signupService.signup(reqA);
                Long tenantAId = tenantRepository.findByWorkspaceSlug("ta-" + uniqueIdA).orElseThrow().getId();
                verifyUserEmail("adminA-" + uniqueIdA + "@ta.com");
                String tokenA = login("adminA-" + uniqueIdA + "@ta.com", "password123",
                                Objects.requireNonNull(resA.getCompanyCode()), tenantAId);

                String uniqueIdB = UUID.randomUUID().toString().substring(0, 8);
                SignupRequest reqB = createSignupRequest("Tenant B " + uniqueIdB, "tb-" + uniqueIdB,
                                "adminB-" + uniqueIdB + "@tb.com");
                signupService.signup(reqB);

                // 1. Tenant A accesses the user list, should only see Tenant A users
                mockMvc.perform(get("/api/v1/tenant/users")
                                .header("Authorization", "Bearer " + tokenA)
                                .with(tenant(tenantAId)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content.length()").value(1))
                                .andExpect(jsonPath("$.content[0].email").value("adminA-" + uniqueIdA + "@ta.com"));
        }

        private SignupRequest createSignupRequest(String name, String workspaceSlug, String email) {
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
