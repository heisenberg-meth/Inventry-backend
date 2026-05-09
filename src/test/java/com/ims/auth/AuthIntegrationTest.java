package com.ims.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ims.BaseIntegrationTest;
import com.ims.dto.request.SignupRequest;
import com.ims.shared.auth.SignupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@AutoConfigureMockMvc
public class AuthIntegrationTest extends BaseIntegrationTest {

  @Autowired private SignupService signupService;

  @BeforeEach
  void setup() {
    cleanupDatabase();
  }

  @Test
  void testSecurityAndIsolationFlow() throws Exception {
    // 1. Signup Tenant 1
    SignupRequest t1Signup =
        createSignupRequest("Unique Business 1", "unique-t1-auth", "admin1@t1.com");
    com.ims.dto.response.SignupResponse t1Response = signupService.signup(t1Signup);

    // 2. Signup Tenant 2
    SignupRequest t2Signup =
        createSignupRequest("Unique Business 2", "unique-t2-auth", "admin2@t2.com");
    com.ims.dto.response.SignupResponse t2Response = signupService.signup(t2Signup);

    // 3. Verify users (simulating email verification)
    verifyUser("admin1@t1.com");
    verifyUser("admin2@t2.com");

    // 4. Login Tenant 1
    String t1Token = login("admin1@t1.com", "password123", t1Response.getCompanyCode());

    // 5. Verify Tenant 1 Isolation (Should only see 1 user: admin1)
    mockMvc
        .perform(get("/api/v1/tenant/users").header("Authorization", "Bearer " + t1Token))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].email").value("admin1@t1.com"));

    // 5. Login Tenant 2
    String t2Token = login("admin2@t2.com", "password123", t2Response.getCompanyCode());

    // 7. Verify Tenant 2 Isolation (Should only see 1 user: admin2)
    mockMvc
        .perform(get("/api/v1/tenant/users").header("Authorization", "Bearer " + t2Token))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].email").value("admin2@t2.com"));

    // 8. Verify Logout and Blacklisting
    mockMvc
        .perform(post("/api/auth/logout").header("Authorization", "Bearer " + t1Token))
        .andDo(print())
        .andExpect(status().isOk());

    // Manually blacklist the token in real Redis if needed,
    // but the logout endpoint should have already done it.
    // We just try to access a protected endpoint with the blacklisted token.

    mockMvc
        .perform(get("/api/v1/tenant/users").header("Authorization", "Bearer " + t1Token))
        .andDo(print())
        .andExpect(status().isUnauthorized());
  }

  @Test
  void testUnauthorizedAccess() throws Exception {
    mockMvc
        .perform(get("/api/v1/tenant/users"))
        .andDo(print())
        .andExpect(status().isUnauthorized());
  }
}
