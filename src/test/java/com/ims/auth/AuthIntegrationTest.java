package com.ims.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.BaseIntegrationTest;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.LoginResponse;
import com.ims.shared.auth.SignupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
    "spring.cache.type=none"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@SuppressWarnings("null")
public class AuthIntegrationTest extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private SignupService signupService;

  @BeforeEach
  void setup() {
    cleanupDatabase();
    mockRedisAndCache();
  }

  @Test
  void testSecurityAndIsolationFlow() throws Exception {
    // 1. Signup Tenant 1
    SignupRequest t1Signup = createSignupRequest("Tenant 1", "t1-auth", "admin1@t1.com");
    com.ims.dto.response.SignupResponse t1Response = signupService.signup(t1Signup);

    // 2. Signup Tenant 2
    SignupRequest t2Signup = createSignupRequest("Tenant 2", "t2-auth", "admin2@t2.com");
    com.ims.dto.response.SignupResponse t2Response = signupService.signup(t2Signup);

    // 3. Verify users (simulating email verification)
    verifyUser("admin1@t1.com");
    verifyUser("admin2@t2.com");

    // 4. Login Tenant 1
    String t1Token = login("admin1@t1.com", "password123", t1Response.getCompanyCode());

    // 5. Verify Tenant 1 Isolation (Should only see 1 user: admin1)
    mockMvc
        .perform(get("/api/tenant/users").header("Authorization", "Bearer " + t1Token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].email").value("admin1@t1.com"));

    // 5. Login Tenant 2
    String t2Token = login("admin2@t2.com", "password123", t2Response.getCompanyCode());

    // 7. Verify Tenant 2 Isolation (Should only see 1 user: admin2)
    mockMvc
        .perform(get("/api/tenant/users").header("Authorization", "Bearer " + t2Token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].email").value("admin2@t2.com"));

    // 8. Verify Logout and Blacklisting
    mockMvc
        .perform(post("/api/auth/logout").header("Authorization", "Bearer " + t1Token))
        .andExpect(status().isOk());

    // Mock Redis blacklist check for next request
    doReturn(true).when(redisTemplate).hasKey(anyString());

    mockMvc
        .perform(get("/api/tenant/users").header("Authorization", "Bearer " + t1Token))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void testUnauthorizedAccess() throws Exception {
    mockMvc.perform(get("/api/tenant/users")).andExpect(status().isUnauthorized());
  }

  private SignupRequest createSignupRequest(String name, String workspaceSlug, String email) {
    SignupRequest req = new SignupRequest();
    req.setBusinessName(name);
    req.setBusinessType("Retail");
    req.setWorkspaceSlug(workspaceSlug);
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
    
    String loginJson = objectMapper.writeValueAsString(loginRequest);
    MvcResult result =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginJson))
            .andExpect(status().isOk())
            .andReturn();

    String responseJson = result.getResponse().getContentAsString();
    LoginResponse response = objectMapper.readValue(responseJson, LoginResponse.class);
    return response.getAccessToken();
  }
}
