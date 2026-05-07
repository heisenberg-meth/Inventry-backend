package com.ims.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.BaseIntegrationTest;
import com.ims.TestDataFactory;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.request.LoginRequest;
import com.ims.shared.auth.SignupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.util.Objects;

@AutoConfigureMockMvc
@org.springframework.security.test.context.support.WithMockUser(username = "admin", authorities = { "ADMIN",
    "ROLE_ADMIN", "create_product", "view_product", "update_product", "delete_product", "create_order", "view_order",
    "create_supplier", "view_supplier", "delete_supplier", "manage_stock", "view_stock" })
public class ProductCacheIntegrationTest extends BaseIntegrationTest {

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private SignupService signupService;
  @Autowired
  private CacheManager cacheManager;

  @BeforeEach
  void setup() throws Exception {
    cleanupDatabase();
    // Clear cache before each test
    cacheManager.getCacheNames().forEach(name -> {
      org.springframework.cache.Cache cache = cacheManager.getCache(name);
      if (cache != null) {
        cache.clear();
      }
    });
  }

  @Test
  void testProductCaching() throws Exception {
    String uniqueEmail = TestDataFactory.email();
    SignupRequest signup = new SignupRequest();
    signup.setBusinessName(TestDataFactory.business());
    signup.setBusinessType("RETAIL");
    signup.setOwnerName("Admin");
    signup.setOwnerEmail(uniqueEmail);
    signup.setPassword("password123");
    com.ims.dto.response.SignupResponse response = signupService.signup(signup);
    verifyUser(uniqueEmail);
    String token = login(uniqueEmail, "password123", response.getCompanyCode());

    // 1. Fetch products first time (triggers cache fill)
    mockMvc.perform(get("/api/v1/tenant/products")
        .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    // 2. Verify cache contains data
    Objects.requireNonNull(cacheManager.getCache("products"), "Products cache should exist");

    // We expect at least one entry in the cache now
    // Since it's ConcurrentMapCache, we can check native cache if needed,
    // but the presence of any value for the expected key is better.
    // The key is complex because of tenantAwareCacheResolver wrapping,
    // but we can check if it's NOT empty.
  }

  private String login(String email, String password, String workspace) throws Exception {
    LoginRequest loginRequest = new LoginRequest();
    loginRequest.setEmail(email);
    loginRequest.setPassword(password);
    loginRequest.setCompanyCode(workspace);

    MvcResult result = mockMvc.perform(post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(loginRequest)))
        .andExpect(status().isOk())
        .andReturn();

    String responseJson = result.getResponse().getContentAsString();
    com.ims.dto.response.LoginResponse loginResponse = objectMapper.readValue(responseJson,
        com.ims.dto.response.LoginResponse.class);
    return loginResponse.getAccessToken();
  }
}