package com.ims.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ims.BaseIntegrationTest;
import com.ims.dto.request.CreateProductRequest;
import com.ims.shared.auth.TenantContext;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

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
public class TenantIsolationIntegrationTest extends BaseIntegrationTest {

  @Autowired private com.ims.product.ProductService productService;

  @Autowired private com.ims.tenant.repository.PermissionRepository permissionRepository;

  @Test
  void shouldPreventCrossTenantDataAccess() {
    // 0. Ensure 'view_product' permission exists
    var viewProductPerm =
        permissionRepository
            .findByKey("view_product")
            .orElseGet(
                () ->
                    permissionRepository.save(
                        com.ims.model.Permission.builder()
                            .key("view_product")
                            .description("View products")
                            .build()));

    // 1. Create product in Tenant 1
    var tenant1User = userRepository.findFirstByTenantIdAndRole(testTenant1Id, "ADMIN").get();

    // Assign permission to tenant1User
    tenant1User.getCustomPermissions().add(viewProductPerm);
    userRepository.save(tenant1User);

    com.ims.helper.SecurityTestUtils.setAuthenticatedUser(tenant1User);
    TenantContext.setTenantId(testTenant1Id);

    CreateProductRequest req1 =
        CreateProductRequest.builder()
            .name("Tenant 1 Product")
            .sku("T1-PROD")
            .purchasePrice(BigDecimal.TEN)
            .salePrice(BigDecimal.valueOf(20))
            .build();
    var resp1 = productService.createProduct(req1);
    Long product1Id = resp1.getId();

    // 2. Create product in Tenant 2
    var tenant2 = tenantRepository.findById(testTenant2Id).get();
    var tenant2User = testDataFactory.createUser(tenant2);

    // Assign permission to tenant2User
    tenant2User.getCustomPermissions().add(viewProductPerm);
    userRepository.save(tenant2User);

    com.ims.helper.SecurityTestUtils.setAuthenticatedUser(tenant2User);
    TenantContext.setTenantId(testTenant2Id);

    CreateProductRequest req2 =
        CreateProductRequest.builder()
            .name("Tenant 2 Product")
            .sku("T2-PROD")
            .purchasePrice(BigDecimal.TEN)
            .salePrice(BigDecimal.valueOf(20))
            .build();
    var resp2 = productService.createProduct(req2);
    Long product2Id = resp2.getId();

    // 3. Verify Tenant 2 cannot see Tenant 1 product via getProductById
    com.ims.helper.SecurityTestUtils.setAuthenticatedUser(tenant2User);
    TenantContext.setTenantId(testTenant2Id);
    assertThrows(
        Exception.class,
        () -> {
          productService.getProductById(product1Id);
        },
        "Tenant 2 should not be able to find Tenant 1 product");

    // 4. Verify Tenant 2 cannot see Tenant 1 product via list
    com.ims.helper.SecurityTestUtils.setAuthenticatedUser(tenant2User);
    TenantContext.setTenantId(testTenant2Id);
    var tenant2Products = productService.getProducts(PageRequest.of(0, 10));
    assertTrue(tenant2Products.getContent().stream().noneMatch(p -> p.getId().equals(product1Id)));
    assertTrue(tenant2Products.getContent().stream().anyMatch(p -> p.getId().equals(product2Id)));

    // 5. Verify Tenant 1 cannot see Tenant 2 product via search (NATIVE QUERY TEST)
    com.ims.helper.SecurityTestUtils.setAuthenticatedUser(tenant1User);
    TenantContext.setTenantId(testTenant1Id);

    // Use SKU for more precise search in native query
    var tenant1SearchResults = productService.searchProducts("T2-PROD", PageRequest.of(0, 10));
    assertTrue(
        tenant1SearchResults.getContent().isEmpty(),
        "Tenant 1 should not find Tenant 2 products via search");

    var tenant1OwnSearch = productService.searchProducts("T1-PROD", PageRequest.of(0, 10));
    assertFalse(tenant1OwnSearch.getContent().isEmpty());
    assertEquals(product1Id, tenant1OwnSearch.getContent().get(0).getId());
  }
}
