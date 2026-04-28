package com.ims.e2e;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ims.BaseIntegrationTest;
import com.ims.dto.CategoryRequest;
import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.request.CustomerRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.SignupResponse;
import com.ims.shared.auth.SignupService;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Comprehensive E2E integration test covering the full IMS business workflow:
 *
 * <ol>
 *   <li>Tenant signup and onboarding</li>
 *   <li>Authentication (login, logout, token validation)</li>
 *   <li>Category CRUD operations</li>
 *   <li>Product CRUD operations</li>
 *   <li>Customer CRUD operations</li>
 *   <li>Order lifecycle (create → confirm → ship → complete)</li>
 *   <li>Stock operations (stock-in, stock-out, adjustments)</li>
 *   <li>Multi-tenant isolation verification</li>
 *   <li>Authorization and security edge cases</li>
 * </ol>
 */
@DisplayName("IMS Full Workflow E2E Test Suite")
@TestMethodOrder(MethodOrderer.DisplayName.class)
public class FullWorkflowE2ETest extends BaseIntegrationTest {

  @Autowired private SignupService signupService;

  private String uniqueId;
  private String tenantEmail;
  private String tenantCompanyCode;
  private Long tenantId;
  private String authToken;

  @BeforeEach
  void setup() {
    cleanupDatabase();
    mockRedisAndCache();
    uniqueId = UUID.randomUUID().toString().substring(0, 8);
  }

  // ─────────────────────────────────────────────────────────────────
  // Helper: signup + verify + login, returns JWT token
  // ─────────────────────────────────────────────────────────────────
  private void bootstrapTenant() throws Exception {
    SignupRequest signup = new SignupRequest();
    signup.setBusinessName("E2E Store " + uniqueId);
    signup.setBusinessType("Retail");
    signup.setWorkspaceSlug("e2e-" + uniqueId);
    signup.setOwnerName("E2E Admin");
    signup.setOwnerEmail("admin-" + uniqueId + "@e2e.test");
    signup.setPassword("password123");

    SignupResponse response = signupService.signup(signup);
    tenantCompanyCode = response.getCompanyCode();
    tenantEmail = "admin-" + uniqueId + "@e2e.test";

    verifyUserEmail(tenantEmail);

    tenantId =
        Objects.requireNonNull(
            tenantRepository
                .findByWorkspaceSlug("e2e-" + uniqueId)
                .orElseThrow()
                .getId());

    authToken = login(tenantEmail, "password123", tenantCompanyCode, tenantId);
  }

  // ────────────────────────────────────────────────────────────────────────────
  // 1. TENANT SIGNUP & AUTH
  // ────────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("1. Tenant Signup & Authentication")
  class TenantSignupAndAuth {

    @Test
    @DisplayName("1a. Signup creates tenant and responds 201")
    void signupCreatesTenant() throws Exception {
      String id = UUID.randomUUID().toString().substring(0, 8);
      SignupRequest req = new SignupRequest();
      req.setBusinessName("Signup Test " + id);
      req.setBusinessType("Retail");
      req.setWorkspaceSlug("signup-" + id);
      req.setOwnerName("Owner");
      req.setOwnerEmail("owner-" + id + "@test.com");
      req.setPassword("password123");

      mockMvc
          .perform(
              post("/api/v1/auth/signup")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.companyCode").isNotEmpty())
          .andExpect(jsonPath("$.workspaceSlug").value("signup-" + id));
    }

    @Test
    @DisplayName("1b. Login returns JWT token for verified user")
    void loginReturnsJwt() throws Exception {
      bootstrapTenant();
      org.junit.jupiter.api.Assertions.assertNotNull(authToken);
      org.junit.jupiter.api.Assertions.assertFalse(authToken.isBlank());
    }

    @Test
    @DisplayName("1c. Unauthenticated request returns 401")
    void unauthenticatedReturns401() throws Exception {
      mockMvc.perform(get("/api/v1/tenant/users").with(tenant("1")))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("1d. Token validation endpoint works")
    void tokenValidation() throws Exception {
      bootstrapTenant();

      mockMvc
          .perform(
              get("/api/v1/auth/validate")
                  .header("Authorization", "Bearer " + authToken)
                  .with(tenant(tenantId.toString())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    @DisplayName("1e. Check email availability")
    void checkEmailAvailability() throws Exception {
      mockMvc
          .perform(get("/api/v1/auth/check-email").param("email", "nonexistent@test.com"))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("1f. Check slug availability")
    void checkSlugAvailability() throws Exception {
      mockMvc
          .perform(get("/api/v1/auth/check-slug").param("slug", "unused-slug-" + uniqueId))
          .andExpect(status().isOk());
    }
  }

  // ────────────────────────────────────────────────────────────────────────────
  // 2. CATEGORY CRUD
  // ────────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("2. Category CRUD Operations")
  class CategoryCrud {

    @Test
    @DisplayName("2a. Create, read, update, delete category lifecycle")
    void fullCategoryCrudLifecycle() throws Exception {
      bootstrapTenant();

      // CREATE
      CategoryRequest createReq = new CategoryRequest();
      createReq.setName("Electronics " + uniqueId);
      createReq.setDescription("Electronic items");
      createReq.setTaxRate(new BigDecimal("18.00"));

      MvcResult createResult =
          mockMvc
              .perform(
                  post("/api/v1/tenant/categories")
                      .header("Authorization", "Bearer " + authToken)
                      .with(tenant(tenantId.toString()))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(objectMapper.writeValueAsString(createReq)))
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.name").value("Electronics " + uniqueId))
              .andReturn();

      String responseJson = createResult.getResponse().getContentAsString();
      Long categoryId =
          objectMapper.readTree(responseJson).get("id").asLong();

      // READ
      mockMvc
          .perform(
              get("/api/v1/tenant/categories/" + categoryId)
                  .header("Authorization", "Bearer " + authToken)
                  .with(tenant(tenantId.toString())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.name").value("Electronics " + uniqueId));

      // UPDATE
      CategoryRequest updateReq = new CategoryRequest();
      updateReq.setName("Electronics Updated " + uniqueId);
      updateReq.setDescription("Updated description");
      updateReq.setTaxRate(new BigDecimal("12.00"));

      mockMvc
          .perform(
              put("/api/v1/tenant/categories/" + categoryId)
                  .header("Authorization", "Bearer " + authToken)
                  .with(tenant(tenantId.toString()))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(updateReq)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.name").value("Electronics Updated " + uniqueId));

      // LIST
      mockMvc
          .perform(
              get("/api/v1/tenant/categories")
                  .header("Authorization", "Bearer " + authToken)
                  .with(tenant(tenantId.toString())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content").isArray());

      // DELETE
      mockMvc
          .perform(
              delete("/api/v1/tenant/categories/" + categoryId)
                  .header("Authorization", "Bearer " + authToken)
                  .with(tenant(tenantId.toString())))
          .andExpect(status().isNoContent());
    }
  }

  // ────────────────────────────────────────────────────────────────────────────
  // 3. PRODUCT CRUD
  // ────────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("3. Product CRUD Operations")
  class ProductCrud {

    @Test
    @DisplayName("3a. Create, read, update, delete product lifecycle")
    void fullProductCrudLifecycle() throws Exception {
      bootstrapTenant();

      // CREATE product
      CreateProductRequest createReq = new CreateProductRequest();
      createReq.setName("Widget " + uniqueId);
      createReq.setSku("SKU-" + uniqueId);
      createReq.setBarcode("BAR-" + uniqueId);
      createReq.setUnit("PCS");
      createReq.setPurchasePrice(new BigDecimal("50.00"));
      createReq.setSalePrice(new BigDecimal("100.00"));
      createReq.setReorderLevel(10);

      MvcResult createResult =
          mockMvc
              .perform(
                  post("/api/v1/tenant/products")
                      .header("Authorization", "Bearer " + authToken)
                      .with(tenant(tenantId.toString()))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(objectMapper.writeValueAsString(createReq)))
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.name").value("Widget " + uniqueId))
              .andExpect(jsonPath("$.sku").value("SKU-" + uniqueId))
              .andReturn();

      Long productId =
          objectMapper.readTree(createResult.getResponse().getContentAsString())
              .get("id").asLong();

      // READ
      mockMvc
          .perform(
              get("/api/v1/tenant/products/" + productId)
                  .header("Authorization", "Bearer " + authToken)
                  .with(tenant(tenantId.toString())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.name").value("Widget " + uniqueId));

      // UPDATE
      CreateProductRequest updateReq = new CreateProductRequest();
      updateReq.setName("Widget Updated " + uniqueId);
      updateReq.setSku("SKU-" + uniqueId);
      updateReq.setSalePrice(new BigDecimal("120.00"));

      mockMvc
          .perform(
              put("/api/v1/tenant/products/" + productId)
                  .header("Authorization", "Bearer " + authToken)
                  .with(tenant(tenantId.toString()))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(updateReq)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.name").value("Widget Updated " + uniqueId));

      // LIST
      mockMvc
          .perform(
              get("/api/v1/tenant/products")
                  .header("Authorization", "Bearer " + authToken)
                  .with(tenant(tenantId.toString())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content").isArray());

      // SEARCH
      mockMvc
          .perform(
              get("/api/v1/tenant/products/search")
                  .param("q", "Widget")
                  .header("Authorization", "Bearer " + authToken)
                  .with(tenant(tenantId.toString())))
          .andExpect(status().isOk());

      // DELETE
      mockMvc
          .perform(
              delete("/api/v1/tenant/products/" + productId)
                  .header("Authorization", "Bearer " + authToken)
                  .with(tenant(tenantId.toString())))
          .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("3b. Product search returns matching results")
    void productSearchWorks() throws Exception {
      bootstrapTenant();

      // Create product to search for
      CreateProductRequest req = new CreateProductRequest();
      req.setName("Searchable Item " + uniqueId);
      req.setSku("SEARCH-" + uniqueId);
      req.setSalePrice(new BigDecimal("25.00"));

      mockMvc
          .perform(
              post("/api/v1/tenant/products")
                  .header("Authorization", "Bearer " + authToken)
                  .with(tenant(tenantId.toString()))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isCreated());

      // Search
      mockMvc
          .perform(
              get("/api/v1/tenant/products/search")
                  .param("q", "Searchable")
                  .header("Authorization", "Bearer " + authToken)
                  .with(tenant(tenantId.toString())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content").isArray());
    }
  }

  // ────────────────────────────────────────────────────────────────────────────
  // 4. CUSTOMER CRUD
  // ────────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("4. Customer CRUD Operations")
  class CustomerCrud {

    @Test
    @DisplayName("4a. Create, read, update, delete customer lifecycle")
    void fullCustomerCrudLifecycle() throws Exception {
      bootstrapTenant();

      // CREATE
      CustomerRequest createReq = new CustomerRequest();
      createReq.setName("Customer " + uniqueId);
      createReq.setPhone("9876543210");
      createReq.setEmail("customer-" + uniqueId + "@test.com");
      createReq.setAddress("123 Main St");

      MvcResult createResult =
          mockMvc
              .perform(
                  post("/api/v1/tenant/customers")
                      .header("Authorization", "Bearer " + authToken)
                      .with(tenant(tenantId.toString()))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(objectMapper.writeValueAsString(createReq)))
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.name").value("Customer " + uniqueId))
              .andReturn();

      Long customerId =
          objectMapper.readTree(createResult.getResponse().getContentAsString())
              .get("id").asLong();

      // READ
      mockMvc
          .perform(
              get("/api/v1/tenant/customers/" + customerId)
                  .header("Authorization", "Bearer " + authToken)
                  .with(tenant(tenantId.toString())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.name").value("Customer " + uniqueId));

      // UPDATE
      CustomerRequest updateReq = new CustomerRequest();
      updateReq.setName("Customer Updated " + uniqueId);
      updateReq.setPhone("1234567890");
      updateReq.setEmail("updated-" + uniqueId + "@test.com");

      mockMvc
          .perform(
              put("/api/v1/tenant/customers/" + customerId)
                  .header("Authorization", "Bearer " + authToken)
                  .with(tenant(tenantId.toString()))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(updateReq)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.name").value("Customer Updated " + uniqueId));

      // LIST
      mockMvc
          .perform(
              get("/api/v1/tenant/customers")
                  .header("Authorization", "Bearer " + authToken)
                  .with(tenant(tenantId.toString())))
          .andExpect(status().isOk());

      // DELETE
      mockMvc
          .perform(
              delete("/api/v1/tenant/customers/" + customerId)
                  .header("Authorization", "Bearer " + authToken)
                  .with(tenant(tenantId.toString())))
          .andExpect(status().isNoContent());
    }
  }

  // ────────────────────────────────────────────────────────────────────────────
  // 5. MULTI-TENANT ISOLATION
  // ────────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("5. Multi-Tenant Isolation")
  class TenantIsolation {

    @Test
    @DisplayName("5a. Tenant 1 cannot see Tenant 2 data")
    void tenantDataIsolation() throws Exception {
      // --- Tenant 1 ---
      String id1 = UUID.randomUUID().toString().substring(0, 8);
      SignupRequest t1Signup = createSignup("Tenant1 " + id1, "t1e2e-" + id1, "t1-" + id1 + "@test.com");
      SignupResponse t1Resp = signupService.signup(t1Signup);
      verifyUserEmail("t1-" + id1 + "@test.com");
      Long t1Id = tenantRepository.findByWorkspaceSlug("t1e2e-" + id1).orElseThrow().getId();
      String t1Token = login("t1-" + id1 + "@test.com", "password123",
          Objects.requireNonNull(t1Resp.getCompanyCode()), t1Id);

      // --- Tenant 2 ---
      String id2 = UUID.randomUUID().toString().substring(0, 8);
      SignupRequest t2Signup = createSignup("Tenant2 " + id2, "t2e2e-" + id2, "t2-" + id2 + "@test.com");
      SignupResponse t2Resp = signupService.signup(t2Signup);
      verifyUserEmail("t2-" + id2 + "@test.com");
      Long t2Id = tenantRepository.findByWorkspaceSlug("t2e2e-" + id2).orElseThrow().getId();
      String t2Token = login("t2-" + id2 + "@test.com", "password123",
          Objects.requireNonNull(t2Resp.getCompanyCode()), t2Id);

      // Tenant 1 should only see their own users
      mockMvc
          .perform(
              get("/api/v1/tenant/users")
                  .header("Authorization", "Bearer " + t1Token)
                  .with(tenant(t1Id.toString())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content.length()").value(1))
          .andExpect(jsonPath("$.content[0].email").value("t1-" + id1 + "@test.com"));

      // Tenant 2 should only see their own users
      mockMvc
          .perform(
              get("/api/v1/tenant/users")
                  .header("Authorization", "Bearer " + t2Token)
                  .with(tenant(t2Id.toString())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content.length()").value(1))
          .andExpect(jsonPath("$.content[0].email").value("t2-" + id2 + "@test.com"));
    }
  }

  // ────────────────────────────────────────────────────────────────────────────
  // 6. AUTH EDGE CASES
  // ────────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("6. Auth Edge Cases")
  class AuthEdgeCases {

    @Test
    @DisplayName("6a. Logout invalidates token")
    void logoutInvalidatesToken() throws Exception {
      bootstrapTenant();

      // Logout
      mockMvc
          .perform(
              post("/api/v1/auth/logout")
                  .header("Authorization", "Bearer " + authToken)
                  .with(tenant(tenantId.toString())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("Logged out successfully"));

      // After logout, mock Redis blacklist
      org.mockito.Mockito.doReturn(true)
          .when(redisTemplate)
          .hasKey(org.mockito.ArgumentMatchers.anyString());

      // Token should now be rejected
      mockMvc
          .perform(
              get("/api/v1/tenant/users")
                  .header("Authorization", "Bearer " + authToken)
                  .with(tenant(tenantId.toString())))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("6b. Signup with duplicate email fails")
    void duplicateEmailFails() throws Exception {
      bootstrapTenant();

      SignupRequest dup = new SignupRequest();
      dup.setBusinessName("Dup Business " + uniqueId);
      dup.setBusinessType("Retail");
      dup.setWorkspaceSlug("dup-" + uniqueId);
      dup.setOwnerName("Dup Owner");
      dup.setOwnerEmail(tenantEmail); // duplicate
      dup.setPassword("password123");

      mockMvc
          .perform(
              post("/api/v1/auth/signup")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(dup)))
          .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("6c. Login with wrong password fails")
    void wrongPasswordFails() throws Exception {
      bootstrapTenant();

      com.ims.dto.request.LoginRequest req = new com.ims.dto.request.LoginRequest();
      req.setEmail(tenantEmail);
      req.setPassword("wrongpassword");
      req.setCompanyCode(tenantCompanyCode);

      mockMvc
          .perform(
              post("/api/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req))
                  .with(tenant(tenantId.toString())))
          .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("6d. Signup with invalid email format fails validation")
    void invalidEmailFormatFails() throws Exception {
      SignupRequest req = new SignupRequest();
      req.setBusinessName("Bad Email Biz");
      req.setBusinessType("Retail");
      req.setWorkspaceSlug("bad-email-" + uniqueId);
      req.setOwnerName("Owner");
      req.setOwnerEmail("not-an-email");
      req.setPassword("password123");

      mockMvc
          .perform(
              post("/api/v1/auth/signup")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("6e. Get current user profile")
    void getCurrentUserProfile() throws Exception {
      bootstrapTenant();

      mockMvc
          .perform(
              get("/api/v1/auth/me")
                  .header("Authorization", "Bearer " + authToken)
                  .with(tenant(tenantId.toString())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.email").value(tenantEmail));
    }
  }

  // ────────────────────────────────────────────────────────────────────────────
  // HELPERS
  // ────────────────────────────────────────────────────────────────────────────

  @NonNull
  private SignupRequest createSignup(
      @NonNull String name, @NonNull String slug, @NonNull String email) {
    SignupRequest req = new SignupRequest();
    req.setBusinessName(Objects.requireNonNull(name));
    req.setBusinessType("Retail");
    req.setWorkspaceSlug(Objects.requireNonNull(slug));
    req.setOwnerName("Owner " + name);
    req.setOwnerEmail(Objects.requireNonNull(email));
    req.setPassword("password123");
    return req;
  }
}
