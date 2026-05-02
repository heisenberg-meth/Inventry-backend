package com.ims.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.ims.BaseIntegrationTest;
import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.SignupResponse;
import com.ims.shared.auth.SignupService;
import java.math.BigDecimal;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ProductCacheIntegrationTest extends BaseIntegrationTest {

  @Autowired
  private SignupService signupService;

  @Override
  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    cleanupDatabase();
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
    SignupResponse response = signupService.signup(signup);
    verifyUserEmail("admin@cache.com");
    verifyUser("admin@cache.com");
    Long tenantId = tenantRepository.findByWorkspaceSlug("cache-corp").orElseThrow().getId();
    String token = login("admin@cache.com", "password123",
        Objects.requireNonNull(response.getCompanyCode()),
        tenantId);

    // 1. Create Product
    CreateProductRequest createReq = new CreateProductRequest();
    createReq.setName("Cached Product");
    createReq.setSku("CACHE-001");
    createReq.setSalePrice(new BigDecimal("10.00"));

    mockMvc
        .perform(
            post("/api/v1/tenant/products")
                .header("Authorization", "Bearer " + token)
                .with(tenant(Objects.requireNonNull(String.valueOf(tenantId)))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Cached Product"));
  }
}