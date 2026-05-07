package com.ims.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.BaseIntegrationTest;
import com.ims.TestDataFactory;
import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.LoginResponse;
import com.ims.shared.auth.SignupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
public class RbacIntegrationTest extends BaseIntegrationTest {

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private SignupService signupService;

  private String adminToken;
  private String managerToken;
  private String staffToken;
  private String companyCode;

  @BeforeEach
  void setup() throws Exception {
    cleanupDatabase();

    // 1. Signup a new tenant (owner is ADMIN)
    String uniqueEmail = TestDataFactory.email();
    String uniqueSlug = "rbac-" + UUID.randomUUID().toString().substring(0, 8);
    com.ims.dto.response.SignupResponse response = signupService.signup(
        createSignupRequest(TestDataFactory.business(), uniqueSlug, uniqueEmail));
    verifyUser(uniqueEmail);
    companyCode = response.getCompanyCode();
    adminToken = login(uniqueEmail, "password123", companyCode);

    // 2. Create a MANAGER user in the same tenant
    String managerEmail = "manager_" + TestDataFactory.email();
    jdbcTemplate.update(
        "INSERT INTO users (name, email, password_hash, role, scope, tenant_id, is_active, is_verified, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
        "Manager User", managerEmail, passwordEncoder.encode("password123"), "MANAGER", "TENANT", testTenant1Id, true, true);
    managerToken = login(managerEmail, "password123", companyCode);

    // 3. Create a STAFF user in the same tenant
    String staffEmail = "staff_" + TestDataFactory.email();
    jdbcTemplate.update(
        "INSERT INTO users (name, email, password_hash, role, scope, tenant_id, is_active, is_verified, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
        "Staff User", staffEmail, passwordEncoder.encode("password123"), "STAFF", "TENANT", testTenant1Id, true, true);
    staffToken = login(staffEmail, "password123", companyCode);
  }

  @Test
  @DisplayName("ADMIN should have full access")
  void adminFullAccess() throws Exception {
    CreateProductRequest createReq = createProductRequest("Admin Product");

    mockMvc.perform(post("/api/v1/tenant/products")
        .header("Authorization", "Bearer " + adminToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(createReq)))
        .andExpect(status().isCreated());

    mockMvc.perform(get("/api/v1/tenant/products")
        .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("MANAGER should be able to create products")
  void managerCanCreate() throws Exception {
    CreateProductRequest createReq = createProductRequest("Manager Product");

    mockMvc.perform(post("/api/v1/tenant/products")
        .header("Authorization", "Bearer " + managerToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(createReq)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("STAFF should NOT be able to create products")
  void staffCannotCreate() throws Exception {
    CreateProductRequest createReq = createProductRequest("Staff Product");

    mockMvc.perform(post("/api/v1/tenant/products")
        .header("Authorization", "Bearer " + staffToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(createReq)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("STAFF should be able to list products")
  void staffCanList() throws Exception {
    mockMvc.perform(get("/api/v1/tenant/products")
        .header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isOk());
  }

  private CreateProductRequest createProductRequest(String name) {
    CreateProductRequest createReq = new CreateProductRequest();
    createReq.setName(name);
    createReq.setSku("SKU-" + UUID.randomUUID().toString().substring(0, 8));
    createReq.setSalePrice(new BigDecimal("10.00"));
    return createReq;
  }

  private SignupRequest createSignupRequest(String name, String slug, String email) {
    SignupRequest signup = new SignupRequest();
    signup.setBusinessName(name);
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

    MvcResult result = mockMvc.perform(post("/api/auth/login")
        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
        .content(Objects.requireNonNull(objectMapper.writeValueAsString(loginRequest))))
        .andExpect(status().isOk())
        .andReturn();

    LoginResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), LoginResponse.class);
    return response.getAccessToken();
  }
}
