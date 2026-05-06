package com.ims.management;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.Objects;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.BaseIntegrationTest;
import com.ims.TestDataFactory;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.LoginResponse;
import com.ims.dto.response.SignupResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc

public class ManagementIntegrationTest extends BaseIntegrationTest {

        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private ObjectMapper objectMapper;
        @Autowired
        private com.ims.shared.auth.SignupService signupService;

        @BeforeEach
        void setup() {
                cleanupDatabase();
                mockRedisAndCache();
        }

        @Test
        void testPlatformAdminFlow() throws Exception {
            String token = login("root@test.com", "root123", null);

                // ROOT can list tenants
                mockMvc
                                .perform(get("/api/platform/tenants").header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk());
        }

        @Test
        void testTenantAdminFlow() throws Exception {
                // 1. Signup Tenant 1
                String uniqueEmail = TestDataFactory.email();
                String uniqueSlug = TestDataFactory.slug();

                SignupRequest t1Signup = createSignupRequest("Tenant 1", uniqueSlug, uniqueEmail);
                com.ims.dto.response.SignupResponse response = signupService.signup(t1Signup);
                verifyUser(uniqueEmail);
                String t1Token = login(uniqueEmail, "password123", response.getCompanyCode());

                // 2. Tenant ADMIN can access their tenant endpoints
                mockMvc
                                .perform(
                                                get("/api/tenant/settings")
                                                                .header("Authorization", "Bearer " + t1Token))
                                .andExpect(status().isOk());
        }

        @Test
        void testIsolationBetweenTenants() throws Exception {

                // Tenant 1
                SignupResponse r1 = signupService
                                .signup(createSignupRequest("Tenant 1-Iso", "t1-iso", "admin-iso1@t1.com"));
                verifyUser("admin-iso1@t1.com");
                String t1Token = login("admin-iso1@t1.com", "password123", r1.getCompanyCode());

                // Tenant 2
                SignupResponse r2 = signupService
                                .signup(createSignupRequest("Tenant 2-Iso", "t2-iso", "admin-iso2@t2.com"));
                verifyUser("admin-iso2@t2.com");
                login("admin-iso2@t2.com", "password123", r2.getCompanyCode());

                // Verify isolation
                mockMvc.perform(get("/api/tenant/users")
                                .header("Authorization", "Bearer " + t1Token))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content.length()").value(1))
                                .andExpect(jsonPath("$.content[0].email").value("admin-iso1@t1.com"));
        }

        @Test
        void testRBACEnforcement() throws Exception {

                SignupResponse r1 = signupService
                                .signup(createSignupRequest("Tenant 1-RBAC", "t1-rbac", "admin-rbac1@t1.com"));

                verifyUser("admin-rbac1@t1.com");

                String t1Token = login("admin-rbac1@t1.com", "password123", r1.getCompanyCode());

                mockMvc.perform(get("/api/platform/tenants")
                                .header("Authorization", "Bearer " + t1Token))
                                .andExpect(status().isForbidden());
        }

        private SignupRequest createSignupRequest(String name, String workspaceSlug, String email) {
                SignupRequest req = new SignupRequest();
                req.setBusinessName(name);
                req.setBusinessType("Retail");
                req.setOwnerName("Owner " + name);
                req.setOwnerEmail(email);
                req.setPassword("password123");
                return req;
        }

        private String login(String email, String password, String workspace) throws Exception {
                LoginRequest loginRequest = new LoginRequest();
                loginRequest.setEmail(email);
                loginRequest.setPassword(password);
                loginRequest.setCompanyCode(workspace);

                MvcResult result = mockMvc
                                .perform(
                                                post("/api/auth/login")
                                                                .contentType(Objects.requireNonNull(
                                                                                MediaType.APPLICATION_JSON))
                                                                .content(Objects.requireNonNull(objectMapper
                                                                                .writeValueAsString(loginRequest))))
                                .andExpect(status().isOk())
                                .andReturn();

                String responseJson = result.getResponse().getContentAsString();
                LoginResponse response = objectMapper.readValue(responseJson, LoginResponse.class);
                return response.getAccessToken();
        }
}
