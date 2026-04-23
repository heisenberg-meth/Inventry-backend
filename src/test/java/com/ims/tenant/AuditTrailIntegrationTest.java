package com.ims.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.BaseIntegrationTest;
import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.LoginResponse;
import com.ims.dto.response.ProductResponse;
import com.ims.shared.auth.SignupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import java.math.BigDecimal;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration",
    "spring.cache.type=none"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuditTrailIntegrationTest extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private SignupService signupService;

  @BeforeEach
  void setup() {
    cleanupDatabase();
    mockRedisAndCache();
  }

  @Test
  void testProductAuditLogging() throws Exception {
    SignupRequest signup = new SignupRequest();
    signup.setBusinessName("Audit Corp");
    signup.setWorkspaceSlug("audit-corp");
    signup.setBusinessType("RETAIL");
    signup.setOwnerName("Admin");
    signup.setOwnerEmail("admin@audit.com");
    signup.setPassword("password123");
    com.ims.dto.response.SignupResponse response = signupService.signup(signup);
    verifyUserEmail("admin@audit.com");
    verifyUser("admin@audit.com");
    
    String token = login("admin@audit.com", "password123", response.getCompanyCode());

    // 1. Create Product
    CreateProductRequest createReq = new CreateProductRequest();
    createReq.setName("Audit Product");
    createReq.setSku("AUDIT-001");
    createReq.setSalePrice(new BigDecimal("10.00"));
    
    String requestJson = objectMapper.writeValueAsString(createReq);
    MvcResult result = mockMvc.perform(post("/api/tenant/products")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isCreated())
        .andReturn();
    
    ProductResponse product = objectMapper.readValue(result.getResponse().getContentAsString(), ProductResponse.class);

    // 2. Verify Audit Log for creation
    mockMvc.perform(get("/api/tenant/audits")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.action == 'CREATE')]").exists());

    // 3. Update Product
    createReq.setName("Updated Audit Product");
    String updateJson = objectMapper.writeValueAsString(createReq);
    mockMvc.perform(put("/api/tenant/products/" + product.getId())
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateJson))
        .andExpect(status().isOk());

    // 4. Verify Audit Log for update
    mockMvc.perform(get("/api/tenant/audits")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.action == 'UPDATE')]").exists());
  }

  @Test
  void testAuditIsolation() throws Exception {
    // Tenant 1
    com.ims.dto.response.SignupResponse r1 = signupService.signup(createSignupRequest("T1", "t1-audit", "admin@t1.com"));
    verifyUserEmail("admin@t1.com");
    verifyUser("admin@t1.com");
    String t1Token = login("admin@t1.com", "password123", r1.getCompanyCode());
    
    // Tenant 2
    com.ims.dto.response.SignupResponse r2 = signupService.signup(createSignupRequest("T2", "t2-audit", "admin@t2.com"));
    verifyUserEmail("admin@t2.com");
    verifyUser("admin@t2.com");
    String t2Token = login("admin@t2.com", "password123", r2.getCompanyCode());

    // T1 performs an action
    CreateProductRequest createReq = new CreateProductRequest();
    createReq.setName("T1 Product");
    createReq.setSku("T1-001");
    createReq.setSalePrice(new BigDecimal("10.00"));
    String t1ReqJson = objectMapper.writeValueAsString(createReq);
    mockMvc.perform(post("/api/tenant/products")
            .header("Authorization", "Bearer " + t1Token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(t1ReqJson))
        .andExpect(status().isCreated());

    // T1 should see 4 logs (Signup + Login + Create + Category Create)
    mockMvc.perform(get("/api/tenant/audits").header("Authorization", "Bearer " + t1Token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(4));

    // T2 should see 2 logs (Signup + Login)
    mockMvc.perform(get("/api/tenant/audits").header("Authorization", "Bearer " + t2Token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2));
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

  private String login(String email, String password, String workspace) throws Exception {
    LoginRequest loginRequest = new LoginRequest();
    loginRequest.setEmail(email);
    loginRequest.setPassword(password);
    loginRequest.setCompanyCode(workspace);
    
    String loginJson = objectMapper.writeValueAsString(loginRequest);
    MvcResult result = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginJson))
        .andExpect(status().isOk())
        .andReturn();

    LoginResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), LoginResponse.class);
    return response.getAccessToken();
  }
}
