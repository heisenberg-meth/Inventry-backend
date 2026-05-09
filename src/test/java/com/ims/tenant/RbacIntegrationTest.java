package com.ims.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ims.BaseIntegrationTest;
import com.ims.TestDataFactory;
import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.response.SignupResponse;
import com.ims.shared.auth.SignupService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class RbacIntegrationTest extends BaseIntegrationTest {

  @Autowired private SignupService signupService;
  @Autowired private com.ims.shared.auth.UserCreationService userCreationService;
  @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

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
    SignupResponse response =
        signupService.signup(
            createSignupRequest(TestDataFactory.business(), uniqueSlug, uniqueEmail));
    verifyUser(uniqueEmail);
    companyCode = response.getCompanyCode();
    adminToken = login(uniqueEmail, "password123", companyCode);
    Long tenantId = response.getTenantId();

    // 2. Create a MANAGER user in the same tenant
    String managerEmail = "manager_" + TestDataFactory.email();
    com.ims.model.User manager =
        com.ims.model.User.builder()
            .name("Manager User")
            .email(managerEmail)
            .passwordHash(passwordEncoder.encode("password123"))
            .role("MANAGER")
            .scope("TENANT")
            .tenantId(tenantId)
            .isActive(true)
            .isVerified(true)
            .build();
    userCreationService.createUserForTenant(manager, tenantId);
    managerToken = login(managerEmail, "password123", companyCode);

    // 3. Create a STAFF user in the same tenant
    String staffEmail = "staff_" + TestDataFactory.email();
    com.ims.model.User staff =
        com.ims.model.User.builder()
            .name("Staff User")
            .email(staffEmail)
            .passwordHash(passwordEncoder.encode("password123"))
            .role("STAFF")
            .scope("TENANT")
            .tenantId(tenantId)
            .isActive(true)
            .isVerified(true)
            .build();
    userCreationService.createUserForTenant(staff, tenantId);
    staffToken = login(staffEmail, "password123", companyCode);
  }

  @Test
  @DisplayName("ADMIN should have full access")
  void adminFullAccess() throws Exception {
    CreateProductRequest createReq = createProductRequest("Admin Product");

    mockMvc
        .perform(
            post("/api/v1/tenant/products")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/v1/tenant/products").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("MANAGER should be able to create products")
  void managerCanCreate() throws Exception {
    CreateProductRequest createReq = createProductRequest("Manager Product");

    mockMvc
        .perform(
            post("/api/v1/tenant/products")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("STAFF should NOT be able to create products")
  void staffCannotCreate() throws Exception {
    CreateProductRequest createReq = createProductRequest("Staff Product");

    mockMvc
        .perform(
            post("/api/v1/tenant/products")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("STAFF should be able to list products")
  void staffCanList() throws Exception {
    mockMvc
        .perform(get("/api/v1/tenant/products").header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isOk());
  }

  private CreateProductRequest createProductRequest(String name) {
    CreateProductRequest createReq = new CreateProductRequest();
    createReq.setName(name);
    createReq.setSku("SKU-" + UUID.randomUUID().toString().substring(0, 8));
    createReq.setSalePrice(new BigDecimal("10.00"));
    return createReq;
  }
}
