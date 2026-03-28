package com.ims.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.LoginResponse;
import com.ims.platform.repository.TenantRepository;
import com.ims.shared.auth.SignupService;
import com.ims.tenant.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private SignupService signupService;
  @Autowired private UserRepository userRepository;
  @Autowired private TenantRepository tenantRepository;

  @MockBean private RedisTemplate<String, Object> redisTemplate;
  @MockBean private ValueOperations<String, Object> valueOperations;
  @MockBean private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;

  @BeforeEach
  void setup() {
    userRepository.deleteAll();
    tenantRepository.deleteAll();
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
  }

  @Test
  void testSecurityAndIsolationFlow() throws Exception {
    // 1. Signup Tenant 1
    SignupRequest t1Signup = createSignupRequest("Tenant 1", "t1", "admin1@t1.com");
    signupService.signup(t1Signup);

    // 2. Signup Tenant 2
    SignupRequest t2Signup = createSignupRequest("Tenant 2", "t2", "admin2@t2.com");
    signupService.signup(t2Signup);

    // 3. Login Tenant 1
    String t1Token = login("admin1@t1.com", "password123");

    // 4. Verify Tenant 1 Isolation (Should only see 1 user: admin1)
    mockMvc
        .perform(get("/api/tenant/users").header("Authorization", "Bearer " + t1Token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].email").value("admin1@t1.com"));

    // 5. Login Tenant 2
    String t2Token = login("admin2@t2.com", "password123");

    // 6. Verify Tenant 2 Isolation (Should only see 1 user: admin2)
    mockMvc
        .perform(get("/api/tenant/users").header("Authorization", "Bearer " + t2Token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].email").value("admin2@t2.com"));

    // 7. Verify Logout and Blacklisting
    mockMvc
        .perform(post("/api/auth/logout").header("Authorization", "Bearer " + t1Token))
        .andExpect(status().isOk());

    // Mock Redis blacklist check for next request
    when(redisTemplate.hasKey(anyString())).thenReturn(true);

    mockMvc
        .perform(get("/api/tenant/users").header("Authorization", "Bearer " + t1Token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Token has been revoked"));
  }

  @Test
  void testUnauthorizedAccess() throws Exception {
    mockMvc.perform(get("/api/tenant/users")).andExpect(status().isUnauthorized());
  }

  private SignupRequest createSignupRequest(String name, String domain, String email) {
    SignupRequest req = new SignupRequest();
    req.setBusinessName(name);
    req.setBusinessType("RETAIL");
    req.setDomain(domain);
    req.setOwnerName(name + " Admin");
    req.setOwnerEmail(email);
    req.setPassword("password123");
    return req;
  }

  private String login(String email, String password) throws Exception {
    LoginRequest loginRequest = new LoginRequest();
    loginRequest.setEmail(email);
    loginRequest.setPassword(password);
    
    MvcResult result =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn();

    String responseJson = result.getResponse().getContentAsString();
    LoginResponse response = objectMapper.readValue(responseJson, LoginResponse.class);
    return response.getAccessToken();
  }
}
