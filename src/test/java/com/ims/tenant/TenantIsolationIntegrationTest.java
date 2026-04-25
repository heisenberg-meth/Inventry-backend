package com.ims.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ims.BaseIntegrationTest;
import com.ims.dto.CategoryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    properties = {
      "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration",
      "spring.cache.type=none"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class TenantIsolationIntegrationTest extends BaseIntegrationTest {

  @BeforeEach
  void setup() {
    cleanupDatabase();
    mockRedisAndCache();
  }

  @Test
  void testRequestFailsWithoutTenantHeader() throws Exception {
    mockMvc.perform(get("/api/v1/tenant/categories")).andExpect(status().isUnauthorized());
  }

  @Test
  void testRequestSucceedsWithTenantHeader() throws Exception {
    // We still need a valid JWT token because of SecurityConfig
    String token = login("root@ims.com", "root123", "SYS001", systemTenantId);

    CategoryRequest request1 = new CategoryRequest();
    request1.setName("Test Category");

    mockMvc
        .perform(
            post("/api/v1/tenant/categories")
                .header("Authorization", "Bearer " + token)
                .with(java.util.Objects.requireNonNull(tenant(String.valueOf(testTenant1Id))))
                .contentType(java.util.Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(
                    java.util.Objects.requireNonNull(objectMapper.writeValueAsString(request1))))
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
            post("/api/v1/tenant/categories")
                .header("Authorization", "Bearer " + token)
                .with(tenant(String.valueOf(testTenant1Id)))
                .contentType(java.util.Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(
                    java.util.Objects.requireNonNull(objectMapper.writeValueAsString(request1))))
        .andExpect(status().isCreated());

    // Verify Tenant 1 can see it
    mockMvc
        .perform(
            get("/api/v1/tenant/categories")
                .header("Authorization", "Bearer " + token)
                .with(tenant(String.valueOf(testTenant1Id))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].name").value("Tenant 1 Category"));

    // Verify Tenant 2 cannot see it
    mockMvc
        .perform(
            get("/api/v1/tenant/categories")
                .header("Authorization", "Bearer " + token)
                .with(tenant(String.valueOf(testTenant2Id))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }
}
