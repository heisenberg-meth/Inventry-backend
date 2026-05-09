package com.ims.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ims.BaseIntegrationTest;
import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.SignupResponse;
import com.ims.model.Permission;
import com.ims.model.User;
import com.ims.product.Product;
import com.ims.shared.auth.SignupService;
import com.ims.tenant.repository.PermissionRepository;
import com.ims.tenant.repository.UserRepository;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
public class AuditTrailIntegrationTest extends BaseIntegrationTest {

  @Autowired private SignupService signupService;

  @Autowired private UserRepository userRepository;

  @Autowired private PermissionRepository permissionRepository;

  @BeforeEach
  void setup() {
  }

  private void assignAuditPermissions(String email) {
    User user =
        userRepository
            .findByEmailUnfiltered(email)
            .orElseThrow(() -> new RuntimeException("User not found: " + email));

    Permission auditRead = getOrCreatePermission("AUDIT_READ", "Can read audit logs");
    Permission auditView = getOrCreatePermission("AUDIT_VIEW", "Can view audit log details");
    Permission productView = getOrCreatePermission("view_product", "Can view products");
    Permission productCreate = getOrCreatePermission("create_product", "Can create products");
    Permission productUpdate = getOrCreatePermission("update_product", "Can update products");

    if (user.getCustomPermissions() == null) {
      user.setCustomPermissions(new HashSet<>());
    }
    user.getCustomPermissions().add(auditRead);
    user.getCustomPermissions().add(auditView);
    user.getCustomPermissions().add(productView);
    user.getCustomPermissions().add(productCreate);
    user.getCustomPermissions().add(productUpdate);
    userRepository.save(user);
  }

  private Permission getOrCreatePermission(String key, String description) {
    return permissionRepository
        .findByKey(key)
        .orElseGet(
            () ->
                permissionRepository.save(
                    Permission.builder().key(key).description(description).build()));
  }

  @Test
  void testProductAuditLogging() throws Exception {
    // 1. Setup Tenant and Data
    String uniqueEmail = "user_" + UUID.randomUUID() + "@test.com";
    String slug = "audit-t1-" + UUID.randomUUID().toString().substring(0, 8);

    SignupRequest signup = createSignupRequest("Audit Business 1", slug, uniqueEmail);
    SignupResponse response = signupService.signup(signup);
    verifyUser(uniqueEmail);

    // Assign required audit permissions to the user in the database
    assignAuditPermissions(uniqueEmail);

    // Login AFTER permissions are assigned to ensure JWT includes them
    String token = login(uniqueEmail, "password123", response.getCompanyCode());

    // Perform action: Create Product
    CreateProductRequest productRequest = new CreateProductRequest();
    productRequest.setName("Audit Test Product");
    productRequest.setSku("AUDIT-" + UUID.randomUUID().toString().substring(0, 8));
    productRequest.setSalePrice(BigDecimal.valueOf(100));

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/tenant/products")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Tenant-ID", response.getTenantId().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(productRequest)))
            .andDo(print())
            .andExpect(status().isCreated())
            .andReturn();

    Product product =
        objectMapper.readValue(result.getResponse().getContentAsString(), Product.class);

    // Perform action: Update Product
    productRequest.setName("Updated Product Name");
    mockMvc
        .perform(
            put("/api/v1/tenant/products/" + product.getId())
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-ID", response.getTenantId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(productRequest)))
        .andDo(print())
        .andExpect(status().isOk());

    // 2. Verify Audit Log for creation - endpoint requires ADMIN role and/or audit
    // permissions
    mockMvc
        .perform(
            get("/api/v1/tenant/audits")
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-ID", response.getTenantId().toString()))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(
            jsonPath("$.content[?(@.action == 'CREATE' && @.entityType == 'PRODUCT')]").exists());
  }

  @Test
  void testAuditIsolation() throws Exception {
    // 1. Setup two separate tenants
    String email1 = "user1_" + UUID.randomUUID() + "@test.com";
    SignupResponse r1 = signupService.signup(createSignupRequest("T1", "t1-audit", email1));
    verifyUser(email1);
    assignAuditPermissions(email1);
    String t1Token = login(email1, "password123", r1.getCompanyCode());

    String email2 = "user2_" + UUID.randomUUID() + "@test.com";
    SignupResponse r2 = signupService.signup(createSignupRequest("T2", "t2-audit", email2));
    verifyUser(email2);
    assignAuditPermissions(email2);
    String t2Token = login(email2, "password123", r2.getCompanyCode());

    // 2. T1 creates a product
    CreateProductRequest p1 = new CreateProductRequest();
    p1.setName("T1 Product");
    p1.setSku("T1-SKU");
    p1.setSalePrice(BigDecimal.TEN);

    mockMvc
        .perform(
            post("/api/v1/tenant/products")
                .header("Authorization", "Bearer " + t1Token)
                .header("X-Tenant-ID", r1.getTenantId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(p1)))
        .andDo(print())
        .andExpect(status().isCreated());

    // 3. T2 should NOT see T1's audit logs
    mockMvc
        .perform(
            get("/api/v1/tenant/audits")
                .header("Authorization", "Bearer " + t2Token)
                .header("X-Tenant-ID", r2.getTenantId().toString()))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.description contains 'T1 Product')]").doesNotExist());
  }
}
