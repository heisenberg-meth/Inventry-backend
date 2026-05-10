package com.ims.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ims.BaseIntegrationTest;
import com.ims.dto.request.CreateProductRequest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;

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
public class ProductPrdIntegrationTest extends BaseIntegrationTest {

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    // Add missing permissions to the test user created by BaseIntegrationTest
    var user = userRepository.findById(testUserId).get();
    var permissions =
        java.util.List.of(
                "create_product",
                "view_product",
                "update_product",
                "delete_product",
                "manage_stock",
                "view_stock")
            .stream()
            .map(
                key ->
                    com.ims.tenant.repository.PermissionRepository.class
                        .cast(
                            applicationContext.getBean(
                                com.ims.tenant.repository.PermissionRepository.class))
                        .findByKey(key)
                        .orElseGet(
                            () ->
                                com.ims.tenant.repository.PermissionRepository.class
                                    .cast(
                                        applicationContext.getBean(
                                            com.ims.tenant.repository.PermissionRepository.class))
                                    .save(
                                        com.ims.model.Permission.builder()
                                            .key(key)
                                            .description(key)
                                            .build())))
            .collect(java.util.stream.Collectors.toSet());

    user.getCustomPermissions().addAll(permissions);
    userRepository.save(user);

    // Re-authenticate with updated user
    com.ims.helper.SecurityTestUtils.setAuthenticatedUser(user);

    // Regenerate token with updated permissions
    try {
      String companyCode = tenantRepository.findById(testTenant1Id).get().getCompanyCode();
      testUserToken = authTestHelper.login(user.getEmail(), "password123", companyCode);
    } catch (Exception e) {
      throw new RuntimeException("Failed to regenerate test token", e);
    }
  }

  @Test
  void testCreateProduct() throws Exception {
    CreateProductRequest request =
        CreateProductRequest.builder()
            .name("New Product")
            .sku("SKU-" + UUID.randomUUID().toString().substring(0, 8))
            .purchasePrice(BigDecimal.valueOf(50))
            .salePrice(BigDecimal.valueOf(100))
            .build();

    mockMvc
        .perform(
            post("/api/v1/tenant/products")
                .header("Authorization", "Bearer " + testUserToken)
                .header("X-Tenant-ID", testTenant1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("New Product"));
  }

  @Test
  void testGetProduct() throws Exception {
    Product product =
        Product.builder()
            .tenantId(testTenant1Id)
            .name("Get Product")
            .sku("GET-001")
            .salePrice(BigDecimal.TEN)
            .build();
    product = productRepository.save(product);

    mockMvc
        .perform(
            get("/api/v1/tenant/products/" + product.getId())
                .header("Authorization", "Bearer " + testUserToken)
                .header("X-Tenant-ID", testTenant1Id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(product.getId()));
  }

  @Test
  void testUpdateProduct() throws Exception {
    Product product =
        Product.builder()
            .tenantId(testTenant1Id)
            .name("Update Product")
            .sku("UPD-001")
            .salePrice(BigDecimal.TEN)
            .build();
    product = productRepository.save(product);

    CreateProductRequest updateRequest =
        CreateProductRequest.builder()
            .name("Updated Name")
            .sku(product.getSku())
            .salePrice(BigDecimal.valueOf(150))
            .build();

    mockMvc
        .perform(
            put("/api/v1/tenant/products/" + product.getId())
                .header("Authorization", "Bearer " + testUserToken)
                .header("X-Tenant-ID", testTenant1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated Name"))
        .andExpect(jsonPath("$.salePrice").value(150));
  }

  @Test
  void testDeleteProduct() throws Exception {
    Product product =
        Product.builder()
            .tenantId(testTenant1Id)
            .name("Delete Product")
            .sku("DEL-001")
            .salePrice(BigDecimal.TEN)
            .build();
    product = productRepository.save(product);

    mockMvc
        .perform(
            delete("/api/v1/tenant/products/" + product.getId())
                .header("Authorization", "Bearer " + testUserToken)
                .header("X-Tenant-ID", testTenant1Id))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/api/v1/tenant/products/" + product.getId())
                .header("Authorization", "Bearer " + testUserToken)
                .header("X-Tenant-ID", testTenant1Id))
        .andExpect(status().isNotFound());
  }

  @Test
  void testListProducts() throws Exception {
    productRepository.save(
        Product.builder()
            .tenantId(testTenant1Id)
            .name("P1")
            .sku("L-001")
            .salePrice(BigDecimal.TEN)
            .build());
    productRepository.save(
        Product.builder()
            .tenantId(testTenant1Id)
            .name("P2")
            .sku("L-002")
            .salePrice(BigDecimal.TEN)
            .build());

    mockMvc
        .perform(
            get("/api/v1/tenant/products")
                .header("Authorization", "Bearer " + testUserToken)
                .header("X-Tenant-ID", testTenant1Id)
                .param("page", "0")
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.content.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
  }

  @Test
  void testSearchProducts() throws Exception {
    productRepository.save(
        Product.builder()
            .tenantId(testTenant1Id)
            .name("UniqueLaptop")
            .sku("LAP-001")
            .salePrice(BigDecimal.TEN)
            .build());

    mockMvc
        .perform(
            get("/api/v1/tenant/products/search")
                .header("Authorization", "Bearer " + testUserToken)
                .header("X-Tenant-ID", testTenant1Id)
                .param("q", "UniqueLaptop"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].name").value("UniqueLaptop"));
  }

  @Test
  void testLowStockProducts() throws Exception {
    productRepository.save(
        Product.builder()
            .tenantId(testTenant1Id)
            .name("Low Stock Item")
            .sku("LS-001")
            .salePrice(BigDecimal.TEN)
            .stock(0)
            .reorderLevel(10)
            .build());

    mockMvc
        .perform(
            get("/api/v1/tenant/products/low-stock")
                .header("Authorization", "Bearer " + testUserToken)
                .header("X-Tenant-ID", testTenant1Id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
  }

  @Test
  void testDuplicateProduct() throws Exception {
    Product product =
        Product.builder()
            .tenantId(testTenant1Id)
            .name("Dup Me")
            .sku("DUP-001")
            .salePrice(BigDecimal.TEN)
            .build();
    product = productRepository.save(product);

    mockMvc
        .perform(
            post("/api/v1/tenant/products/" + product.getId() + "/duplicate")
                .header("Authorization", "Bearer " + testUserToken)
                .header("X-Tenant-ID", testTenant1Id))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value(product.getName() + " (Copy)"))
        .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(product.getId().intValue())));
  }
}
