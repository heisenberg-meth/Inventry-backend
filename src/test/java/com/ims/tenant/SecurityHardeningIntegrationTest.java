package com.ims.tenant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.BaseIntegrationTest;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
public class SecurityHardeningIntegrationTest extends BaseIntegrationTest {

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private ObjectMapper objectMapper;

  @BeforeEach
  void setup() {
    cleanupDatabase();
    mockRedisAndCache();
  }

  @Test
  void testCorrelationIdInHeadersAndError() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/invalid-path"))
        .andExpect(status().isNotFound())
        .andExpect(header().exists("X-Correlation-ID"))
        .andExpect(jsonPath("$.correlation_id").exists());
  }

  @Test
  void testRateLimitEnforcement() throws Exception {
    // Mock Redis to return 100 requests already made (Public limit is 50)
    doReturn(100L).when(zSetOperations).zCard(Objects.requireNonNull(any(String.class)));

    mockMvc
        .perform(get("/api/any-public-endpoint"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("X-RateLimit-Limit", "50"))
        .andExpect(jsonPath("$.error").value("Too Many Requests"));
  }

  @Test
  void testAuthRateLimitEnforcement() throws Exception {
    // Mock Redis for auth endpoint (Limit is 20)
    doReturn(25L).when(zSetOperations).zCard(Objects.requireNonNull(any(String.class)));

    String authLoginJson = objectMapper.writeValueAsString(
        Map.of("email", "root@ims.com", "password", TEST_ROOT_PASSWORD));
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(authLoginJson)))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("X-RateLimit-Limit", "20"))
        .andExpect(jsonPath("$.error").value("Too Many Requests"));
  }
}
