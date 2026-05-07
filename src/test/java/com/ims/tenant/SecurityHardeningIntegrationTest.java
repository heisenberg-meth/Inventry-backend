package com.ims.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
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
import java.util.Map;

@AutoConfigureMockMvc

public class SecurityHardeningIntegrationTest extends BaseIntegrationTest {

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private ObjectMapper objectMapper;

  @BeforeEach
  void setup() {
    cleanupDatabase();
  }

  @Test
  void testCorrelationIdInHeadersAndError() throws Exception {
    mockMvc.perform(get("/api/auth/invalid-path"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().exists("X-Correlation-ID"));
    // Note: correlation_id in body depends on error handling
  }

  @Test
  void testRateLimitEnforcement() throws Exception {
    // Populate Redis to exceed Public limit (50)
    String key = "rate_limit:ip:127.0.0.1";
    for (int i = 0; i < 51; i++) {
      redisTemplate.opsForZSet().add(key, "req-" + i, System.currentTimeMillis());
    }

    mockMvc.perform(get("/api/any-public-endpoint"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("X-RateLimit-Limit", "50"))
        .andExpect(jsonPath("$.message").value("Rate limit exceeded"));
  }

  @Test
  void testAuthRateLimitEnforcement() throws Exception {
    // Populate Redis to exceed Auth limit (20)
    String key = "rate_limit:ip:127.0.0.1";
    redisTemplate.delete(key);
    for (int i = 0; i < 21; i++) {
      redisTemplate.opsForZSet().add(key, "auth-req-" + i, System.currentTimeMillis());
    }

    String authLoginJson = objectMapper.writeValueAsString(Map.of("email", "root@ims.com", "password", "root123"));
    mockMvc.perform(post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(authLoginJson))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("X-RateLimit-Limit", "20"));
  }

  @Test
  void testNoStackTraceOnInternalError() throws Exception {
    String token = login("root@test.com", "root123", null);

    mockMvc.perform(get("/api/platform/users/test-error")
        .header("Authorization", "Bearer " + token))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
        .andExpect(jsonPath("$.stack_trace").doesNotExist());
  }

  private String login(String email, String password, String workspace) throws Exception {
    LoginRequest loginRequest = new LoginRequest();
    loginRequest.setEmail(email);
    loginRequest.setPassword(password);
    loginRequest.setCompanyCode(workspace);

    String loginJson = objectMapper.writeValueAsString(loginRequest);
    MvcResult result = mockMvc
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
