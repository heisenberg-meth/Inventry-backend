package com.ims.product;

import static org.junit.jupiter.api.Assertions.*;
import com.ims.BaseIntegrationTest;
import com.ims.shared.auth.TenantContext;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

public class ProductPrdIntegrationTest extends BaseIntegrationTest {

        @Autowired
        private ProductRepository productRepository;

        @BeforeEach
        void setUp() {
                cleanupDatabase();

                // Create test tenants
                jdbcTemplate.execute(
                                "INSERT INTO tenants (id, name, workspace_slug, company_code, is_active) VALUES (1, 'Tenant 1', 't1', 'C1', true)");
                jdbcTemplate.execute(
                                "INSERT INTO tenants (id, name, workspace_slug, company_code, is_active) VALUES (2, 'Tenant 2', 't2', 'C2', true)");
        }

        @Test
        void testSkuUniquenessPerTenant() {
                Long tenantId = 1L;
                TenantContext.setTenantId(tenantId);

                Product p1 = Product.builder()
                                .tenantId(tenantId)
                                .name("Product 1")
                                .sku("SKU-001")
                                .salePrice(BigDecimal.TEN)
                                .isDeleted(false)
                                .build();
                productRepository.save(p1);

                // Same SKU, same tenant -> FAIL
                Product p2 = Product.builder()
                                .tenantId(tenantId)
                                .name("Product 2")
                                .sku("SKU-001")
                                .salePrice(BigDecimal.ONE)
                                .isDeleted(false)
                                .build();
                assertThrows(DataIntegrityViolationException.class, () -> productRepository.save(p2));

                // Same SKU, different tenant -> PASS
                Long tenant2Id = 2L;
                Product p3 = Product.builder()
                                .tenantId(tenant2Id)
                                .name("Product 3")
                                .sku("SKU-001")
                                .salePrice(BigDecimal.ONE)
                                .isDeleted(false)
                                .build();
                assertDoesNotThrow(() -> productRepository.save(p3));
        }

        @Test
        void testSkuReuseAfterSoftDelete() {
                Long tenantId = 1L;
                TenantContext.setTenantId(tenantId);

                Product p1 = Product.builder()
                                .tenantId(tenantId)
                                .name("Product 1")
                                .sku("SKU-001")
                                .salePrice(BigDecimal.TEN)
                                .isDeleted(false)
                                .build();
                productRepository.save(p1);

                // Soft delete
                p1.setIsDeleted(true);
                productRepository.save(p1);

                // Reuse SKU -> PASS
                Product p2 = Product.builder()
                                .tenantId(tenantId)
                                .name("Product 2")
                                .sku("SKU-001")
                                .salePrice(BigDecimal.ONE)
                                .isDeleted(false)
                                .build();
                assertDoesNotThrow(() -> productRepository.save(p2));
        }

        @Test
        void testSoftDeleteBehavior() {
                Long tenantId = 1L;
                TenantContext.setTenantId(tenantId);

                Product p1 = Product.builder()
                                .tenantId(tenantId)
                                .name("Active Product")
                                .sku("SKU-ACT")
                                .salePrice(BigDecimal.TEN)
                                .isDeleted(false)
                                .build();
                productRepository.save(p1);

                Product p2 = Product.builder()
                                .tenantId(tenantId)
                                .name("Deleted Product")
                                .sku("SKU-DEL")
                                .salePrice(BigDecimal.TEN)
                                .isDeleted(true)
                                .build();
                productRepository.save(p2);

                var activeProducts = productRepository.findAllWithDetails(tenantId, PageRequest.of(0, 10));
                assertEquals(1, activeProducts.getContent().size());
                assertEquals("Active Product", activeProducts.getContent().get(0).getName());
        }

        @Test
        void testPriceAndStockConstraints() {
                Long tenantId = 1L;
                TenantContext.setTenantId(tenantId);

                // Negative price -> FAIL
                Product p1 = Product.builder()
                                .tenantId(tenantId)
                                .name("Bad Price")
                                .sku("SKU-BAD-P")
                                .salePrice(new BigDecimal("-1.0"))
                                .isDeleted(false)
                                .build();
                assertThrows(DataIntegrityViolationException.class, () -> productRepository.save(p1));

                // Negative stock -> FAIL
                Product p2 = Product.builder()
                                .tenantId(tenantId)
                                .name("Bad Stock")
                                .sku("SKU-BAD-S")
                                .salePrice(BigDecimal.TEN)
                                .stock(-5)
                                .isDeleted(false)
                                .build();
                assertThrows(DataIntegrityViolationException.class, () -> productRepository.save(p2));
        }

        @Test
        void testSearchFastWithSearchVector() {
                Long tenantId = 1L;
                TenantContext.setTenantId(tenantId);

                Product p1 = Product.builder()
                                .tenantId(tenantId)
                                .name("Special Laptop")
                                .description("High-end gaming laptop")
                                .sku("LAP-001")
                                .salePrice(new BigDecimal("1500.00"))
                                .isDeleted(false)
                                .build();
                productRepository.save(p1);

                Product p2 = Product.builder()
                                .tenantId(tenantId)
                                .name("Mouse")
                                .description("Wireless mouse")
                                .sku("MOU-001")
                                .salePrice(new BigDecimal("50.00"))
                                .isDeleted(false)
                                .build();
                productRepository.save(p2);

                var results = productRepository.searchFast(tenantId, "laptop", PageRequest.of(0, 10));
                assertEquals(1, results.getContent().size());
                assertEquals("Special Laptop", results.getContent().get(0).getName());

                results = productRepository.searchFast(tenantId, "gaming", PageRequest.of(0, 10));
                assertEquals(1, results.getContent().size());
                assertEquals("Special Laptop", results.getContent().get(0).getName());
        }
}
