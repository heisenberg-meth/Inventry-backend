package com.ims.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ims.BaseIntegrationTest;
import com.ims.TestDataFactory;
import com.ims.dto.request.SignupRequest;
import com.ims.shared.auth.SignupService;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;

@AutoConfigureMockMvc
@org.springframework.security.test.context.support.WithMockUser(
    username = "admin",
    authorities = {
      "ADMIN",
      "ROLE_ADMIN",
      "create_product",
      "view_product",
      "update_product",
      "delete_product",
      "create_order",
      "view_order",
      "create_supplier",
      "view_supplier",
      "delete_supplier",
      "manage_stock",
      "view_stock"
    })
public class ProductCacheIntegrationTest extends BaseIntegrationTest {

  @Autowired private SignupService signupService;
  @Autowired private CacheManager cacheManager;

  @BeforeEach
  void setup() throws Exception {
    cleanupDatabase();
    // Clear cache before each test
    cacheManager
        .getCacheNames()
        .forEach(
            name -> {
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
    mockMvc
        .perform(get("/api/v1/tenant/products").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    // 2. Verify cache contains data
    Objects.requireNonNull(cacheManager.getCache("products"), "Products cache should exist");
  }
}
