package com.ims.integration;

import com.ims.BaseIntegrationTest;
import com.ims.shared.auth.TenantContext;
import com.ims.dto.request.CreateProductRequest;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

@org.springframework.security.test.context.support.WithMockUser(username = "admin", authorities = { "ADMIN",
        "ROLE_ADMIN", "create_product", "view_product", "update_product", "delete_product", "create_order",
        "view_order", "create_supplier", "view_supplier", "delete_supplier", "manage_stock", "view_stock" })
public class TenantIsolationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private com.ims.product.ProductService productService;

    @Test
    void shouldPreventCrossTenantDataAccess() {
        // 1. Create product in Tenant 1
        TenantContext.setTenantId(testTenant1Id);
        CreateProductRequest req1 = CreateProductRequest.builder()
                .name("Tenant 1 Product")
                .sku("T1-PROD")
                .purchasePrice(BigDecimal.TEN)
                .salePrice(BigDecimal.valueOf(20))
                .build();
        var resp1 = productService.createProduct(req1);
        Long product1Id = resp1.getId();

        // 2. Create product in Tenant 2
        TenantContext.setTenantId(testTenant2Id);
        CreateProductRequest req2 = CreateProductRequest.builder()
                .name("Tenant 2 Product")
                .sku("T2-PROD")
                .purchasePrice(BigDecimal.TEN)
                .salePrice(BigDecimal.valueOf(20))
                .build();
        var resp2 = productService.createProduct(req2);
        Long product2Id = resp2.getId();

        // 3. Verify Tenant 2 cannot see Tenant 1 product via getProductById
        assertThrows(EntityNotFoundException.class, () -> {
            productService.getProductById(product1Id);
        }, "Tenant 2 should not be able to find Tenant 1 product");

        // 4. Verify Tenant 2 cannot see Tenant 1 product via list
        var tenant2Products = productService.getProducts(PageRequest.of(0, 10));
        assertTrue(tenant2Products.getContent().stream().noneMatch(p -> p.getId().equals(product1Id)));
        assertTrue(tenant2Products.getContent().stream().anyMatch(p -> p.getId().equals(product2Id)));

        // 5. Verify Tenant 1 cannot see Tenant 2 product via search (NATIVE QUERY TEST)
        TenantContext.setTenantId(testTenant1Id);
        var tenant1SearchResults = productService.searchProducts("Tenant 2", PageRequest.of(0, 10));
        assertTrue(tenant1SearchResults.getContent().isEmpty(),
                "Tenant 1 should not find Tenant 2 products via search");

        var tenant1OwnSearch = productService.searchProducts("Tenant 1", PageRequest.of(0, 10));
        assertFalse(tenant1OwnSearch.getContent().isEmpty());
        assertEquals(product1Id, tenant1OwnSearch.getContent().get(0).getId());
    }
}
