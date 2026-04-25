package com.ims.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.BaseIntegrationTest;
import com.ims.dto.CategoryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration",
      "spring.cache.type=none"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class TenantIsolationIntegrationTest extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void setup() {
    cleanupDatabase();
    mockRedisAndCache();
  }

  @Test
  void testRequestFailsWithoutTenantHeader() throws Exception {
    mockMvc.perform(get("/tenant/categories")).andExpect(status().isInternalServerError());
    // It throws IllegalStateException which results in 500 by default unless handled
  }

  @Test
  void testRequestSucceedsWithTenantHeader() throws Exception {
    // We still need a valid JWT token because of SecurityConfig
    String token = login("root@ims.com", "root123", "SYS001", systemTenantId);

    CategoryRequest request1 = new CategoryRequest();
    request1.setName("Test Category");

    mockMvc.perform(
    post("/api/tenant/categories")
        .header("Authorization", "Bearer " + token)
        .with(java.util.Objects.requireNonNull(tenant(String.valueOf(testTenant1Id))))
        .contentType(java.util.Objects.requireNonNull(MediaType.APPLICATION_JSON))
        .content(java.util.Objects.requireNonNull(objectMapper.writeValueAsString(request1))))
        .andExpect(status().isCreated());
  }

  @Test
  void testDataIsolationBetweenTenants() throws Exception {
    String token = login("root@ims.com", "root123", "SYS001", systemTenantId);

    // Create category for Tenant 1
    CategoryRequest request1 = new CategoryRequest();
    request1.setName("Tenant 1 Category");

    mockMvc
        .perform(
            post("/api/tenant/categories")
                .header("Authorization", "Bearer " + token)
                .with(tenant(String.valueOf(testTenant1Id)))
                .contentType(java.util.Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(java.util.Objects.requireNonNull(objectMapper.writeValueAsString(request1))))
        .andExpect(status().isCreated());

    // Verify Tenant 1 can see it
    mockMvc
        .perform(
            get("/api/tenant/categories")
                .header("Authorization", "Bearer " + token)
                .with(tenant(String.valueOf(testTenant1Id))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].name").value("Tenant 1 Category"));

    // Verify Tenant 2 cannot see it
    mockMvc
        .perform(
            get("/api/tenant/categories")
                .header("Authorization", "Bearer " + token)
                .with(tenant(String.valueOf(testTenant2Id))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  private String login(String email, String password, String workspace, Long tenantId)
      throws Exception {
    com.ims.dto.request.LoginRequest loginRequest = new com.ims.dto.request.LoginRequest();
    loginRequest.setEmail(email);
    loginRequest.setPassword(password);
    loginRequest.setCompanyCode(workspace);

    String loginJson = objectMapper.writeValueAsString(loginRequest);
    var result =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(java.util.Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(loginJson)
                    .with(tenant(String.valueOf(tenantId))))
            .andExpect(status().isOk())
            .andReturn();

    String responseJson = result.getResponse().getContentAsString();
    com.ims.dto.response.LoginResponse response =
        objectMapper.readValue(responseJson, com.ims.dto.response.LoginResponse.class);
    return response.getAccessToken();
  }
}
