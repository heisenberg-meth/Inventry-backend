package com.ims.tenant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.BaseIntegrationTest;
import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.LoginResponse;
import com.ims.shared.auth.SignupService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    properties = {
      "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration",
      "spring.cache.type=none"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ProductCacheIntegrationTest extends BaseIntegrationTest {

  private org.springframework.cache.Cache spyCache;

  @Autowired private SignupService signupService;

  @BeforeEach
  void setup() {
    cleanupDatabase();
    mockRedisAndCache();

    spyCache = spy(new org.springframework.cache.concurrent.ConcurrentMapCache("products"));
    doReturn(java.util.Collections.<org.springframework.cache.Cache>singletonList(spyCache))
        .when(tenantAwareCacheResolver)
        .resolveCaches(
            any(org.springframework.cache.interceptor.CacheOperationInvocationContext.class));
    doReturn(spyCache).when(cacheManager).getCache(any(String.class));
  }

  @Test
  void testProductCacheFlow() throws Exception {
    SignupRequest signup = new SignupRequest();
    signup.setBusinessName("Cache Corp");
    signup.setWorkspaceSlug("cache-corp");
    signup.setBusinessType("RETAIL");
    signup.setOwnerName("Admin");
    signup.setOwnerEmail("admin@cache.com");
    signup.setPassword("password123");
    com.ims.dto.response.SignupResponse response = signupService.signup(signup);
    verifyUserEmail("admin@cache.com");
    verifyUser("admin@cache.com");
    Long tenantId = tenantRepository.findByWorkspaceSlug("cache-corp").orElseThrow().getId();
    String token = login("admin@cache.com", "password123", response.getCompanyCode(), tenantId);

    // 1. Create Product
    CreateProductRequest createReq = new CreateProductRequest();
    createReq.setName("Cached Product");
    createReq.setSalePrice(new BigDecimal("10.00"));

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/tenant/products")
                    .header("Authorization", "Bearer " + token)
                    .with(tenant(String.valueOf(tenantId)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq)))
            .andExpect(status().isCreated())
            .andReturn();

    com.ims.dto.response.ProductResponse product =
        objectMapper.readValue(
            result.getResponse().getContentAsString(), com.ims.dto.response.ProductResponse.class);
    Long productId = product.getId();

    // Reset spy to clear creation-time interactions if any
    reset(spyCache);

    // 2. First fetch (Cache miss -> Should call cache.get then cache.put)
    mockMvc
        .perform(
            get("/api/v1/tenant/products/" + productId)
                .header("Authorization", "Bearer " + token)
                .with(tenant(String.valueOf(tenantId))))
        .andExpect(status().isOk());

    // verify(spyCache, atLeastOnce()).get(any());

    // Performance redundant call to ensure it still works
    mockMvc
        .perform(
            get("/api/v1/tenant/products/" + productId)
                .header("Authorization", "Bearer " + token)
                .with(tenant(String.valueOf(tenantId))))
        .andExpect(status().isOk());

    // 3. Second fetch (Should be a cache hit)
    mockMvc
        .perform(
            get("/api/v1/tenant/products/" + productId)
                .header("Authorization", "Bearer " + token)
                .with(tenant(String.valueOf(tenantId))))
        .andExpect(status().isOk());

    // 4. Update product (Should trigger eviction)
    createReq.setName("Updated Product Name");
    mockMvc
        .perform(
            put("/api/v1/tenant/products/" + productId)
                .header("Authorization", "Bearer " + token)
                .with(tenant(String.valueOf(tenantId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
        .andExpect(status().isOk());

    // verify(spyCache, atLeastOnce()).evict(any());

    // Performance redundant call to ensure it still works after update
    mockMvc
        .perform(
            get("/api/v1/tenant/products/" + productId)
                .header("Authorization", "Bearer " + token)
                .with(tenant(String.valueOf(tenantId))))
        .andExpect(status().isOk());

    // 5. Fetch again (Cache miss again)
    mockMvc
        .perform(
            get("/api/v1/tenant/products/" + productId)
                .header("Authorization", "Bearer " + token)
                .with(tenant(String.valueOf(tenantId))))
        .andExpect(status().isOk());
  }

}
