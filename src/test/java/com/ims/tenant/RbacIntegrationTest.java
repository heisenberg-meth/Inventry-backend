package com.ims.tenant;

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
import java.util.Objects;
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
public class RbacIntegrationTest extends BaseIntegrationTest {

  @Autowired private SignupService signupService;

  @BeforeEach
  void setup() {
    cleanupDatabase();
  }

  @Test
  void testRBAC() throws Exception {
    com.ims.dto.response.SignupResponse response =
        signupService.signup(createSignupRequest("RBAC Corp", "rbac-corp", "admin@rbac.com"));
    verifyUserEmail("admin@rbac.com");
    verifyUser("admin@rbac.com");
    Long tenantId = tenantRepository.findByWorkspaceSlug("rbac-corp").orElseThrow().getId();
    String token = login("admin@rbac.com", "password123", response.getCompanyCode(), tenantId);

    // 1. Create a product first (with all mandatory fields)
    CreateProductRequest createReq = new CreateProductRequest();
    createReq.setName("RBAC Product");
    createReq.setSku("RBAC-001");
    createReq.setSalePrice(new BigDecimal("10.00"));

    mockMvc
        .perform(
            post("/api/v1/tenant/products")
                .header("Authorization", "Bearer " + token)
                .with(tenant(String.valueOf(tenantId)))
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(createReq))))
        .andExpect(status().isCreated());

    // 2. ADMIN can access products
    mockMvc
        .perform(
            get("/api/v1/tenant/products")
                .header("Authorization", "Bearer " + token)
                .with(tenant(String.valueOf(tenantId))))
        .andExpect(status().isOk());
  }

  private SignupRequest createSignupRequest(String name, String slug, String email) {
    SignupRequest signup = new SignupRequest();
    signup.setBusinessName(name);
    signup.setWorkspaceSlug(slug);
    signup.setBusinessType("RETAIL");
    signup.setOwnerName("Admin");
    signup.setOwnerEmail(email);
    signup.setPassword("password123");
    return signup;
  }
}
