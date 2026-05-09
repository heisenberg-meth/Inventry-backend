package com.ims.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ims.BaseIntegrationTest;
import com.ims.dto.request.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;

@AutoConfigureMockMvc
class FailureHandlingIntegrationTest extends BaseIntegrationTest {

  @BeforeEach
  void setup() {
    cleanupDatabase();
  }

  @Nested
  @DisplayName("11.3 CORS Controlled")
  class CorsTests {

    @Test
    @DisplayName("Unauthorised origin should be blocked")
    void unauthorizedOriginBlocked() throws Exception {
      mockMvc
          .perform(
              post("/api/auth/login")
                  .header("Origin", "https://malicious-site.com")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"invalid\",\"password\":\"invalid\"}"))
          .andExpect(
              result -> {
                int status = result.getResponse().getStatus();
                assertThat(status).isIn(400, 403);
              });
    }

    @Test
    @DisplayName("Security headers should be present")
    void securityHeadersPresent() throws Exception {
      mockMvc
          .perform(get("/api/auth/login"))
          .andExpect(header().string("X-Frame-Options", "DENY"))
          .andExpect(header().string("X-Content-Type-Options", "nosniff"))
          .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }
  }

  @Nested
  @DisplayName("11.4 Input Validation Everywhere")
  class ValidationTests {

    @Test
    @DisplayName("Invalid payload should be rejected with 400")
    void invalidPayloadRejected() throws Exception {
      LoginRequest invalidRequest = new LoginRequest();
      invalidRequest.setEmail("");
      invalidRequest.setPassword("");
      String invalidJson = objectMapper.writeValueAsString(invalidRequest);

      mockMvc
          .perform(
              post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(invalidJson))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Malformed JSON should be rejected")
    void malformedJsonRejected() throws Exception {
      String malformedJson = "{invalid json}";

      mockMvc
          .perform(
              post("/api/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(malformedJson))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("11.5 No Sensitive Data in Logs")
  class LoggingSecurityTests {

    @Test
    @DisplayName("Login response should not contain password")
    void loginResponseDoesNotContainPassword() throws Exception {
      insertPlatformUser();

      LoginRequest loginRequest = new LoginRequest();
      loginRequest.setEmail("root@test.com");
      loginRequest.setPassword("root123");

      String responseJson = mockMvc
          .perform(
              post("/api/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(loginRequest)))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();

      assertThat(responseJson).doesNotContain("root123");
    }

    @Test
    @DisplayName("Error response should not contain stack trace")
    void noStackTraceInErrorResponse() throws Exception {
      String token = login("root@test.com", "root123", null);

      mockMvc
          .perform(get("/api/platform/users/test-error").header("Authorization", "Bearer " + token))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
          .andExpect(jsonPath("$.stack_trace").doesNotExist())
          .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"));
    }
  }

  @Nested
  @DisplayName("11.6 Observability Security")
  class ObservabilityTests {

    @Test
    @DisplayName("Correlation ID should be present in response headers")
    void correlationIdPresentInHeaders() throws Exception {
      mockMvc
          .perform(get("/api/auth/invalid-path"))
          .andExpect(status().isUnauthorized())
          .andExpect(header().exists("X-Correlation-ID"));
    }
  }

  @Nested
  @DisplayName("11.7 API Exposure Rules")
  class ApiExposureTests {

    @Test
    @DisplayName("Actuator endpoints should be restricted to ADMIN")
    void actuatorRestricted() throws Exception {
      mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Actuator health should be publicly accessible")
    void healthPubliclyAccessible() throws Exception {
      mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Swagger should require authentication")
    void swaggerRequiresAuth() throws Exception {
      mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
    }
  }

  private void insertPlatformUser() {
    testDataFactory.createPlatformUser("root@test.com", "root123", "ADMIN");
  }
}
