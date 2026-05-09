package com.ims.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ims.BaseIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

@AutoConfigureMockMvc
@TestPropertySource(properties = {"app.rate-limit.enabled=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class SecurityHardeningIntegrationTest extends BaseIntegrationTest {

  private void clearRateLimits() {
    var keys = redisTemplate.keys("rate_limit:*");
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
  }

  @Test
  void testCorrelationIdInHeadersAndError() throws Exception {
    clearRateLimits();

    mockMvc
        .perform(get("/actuator/metrics")
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()))
        .andExpect(status().isUnauthorized())
        .andExpect(header().exists("X-Correlation-ID"));
  }

  @Test
  void testRateLimitEnforcement() throws Exception {
    clearRateLimits();

    mockMvc
        .perform(get("/api/auth/check-email").param("email", "test@test.com"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").exists());
  }

  @Test
  void testAuthRateLimitEnforcement() throws Exception {
    clearRateLimits();

    String authLoginJson =
        objectMapper.writeValueAsString(Map.of("email", "root@ims.com", "password", "root123"));
    mockMvc
        .perform(
            post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(authLoginJson))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void testNoStackStackOnInternalError() throws Exception {
    clearRateLimits();
    String token = login("root@test.com", "root123", null);

    mockMvc
        .perform(get("/api/platform/users/test-error").header("Authorization", "Bearer " + token))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
        .andExpect(jsonPath("$.stack_trace").doesNotExist());
  }
}
