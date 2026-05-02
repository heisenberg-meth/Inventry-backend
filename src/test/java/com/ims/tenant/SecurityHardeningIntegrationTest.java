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

  @Override
  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    cleanupDatabase();
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
    // Rate limiting is tested at the unit level in RateLimitFilterTest.
    // Here we verify the rate limit headers are present on a normal response
    // to confirm the filter is integrated into the chain.
    mockMvc
        .perform(post("/api/v1/auth/login")
            .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
            .content(objectMapper.writeValueAsString(
                Map.of("email", "root@ims.com", "password", TEST_ROOT_PASSWORD))))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-RateLimit-Limit"))
        .andExpect(header().exists("X-RateLimit-Remaining"));
  }

  @Test
  void testAuthRateLimitEnforcement() throws Exception {
    // Mock Redis for auth endpoint (Limit is 20)
    doReturn(25L).when(zSetOperations).zCard(anyString());

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
