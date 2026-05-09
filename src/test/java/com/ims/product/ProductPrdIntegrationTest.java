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

@org.springframework.security.test.context.support.WithMockUser(username = "admin", authorities = { "ADMIN",
                "ROLE_ADMIN", "create_product", "view_product", "update_product", "delete_product", "create_order",
                "view_order", "create_supplier", "view_supplier", "delete_supplier", "manage_stock", "view_stock" })
public class ProductPrdIntegrationTest extends BaseIntegrationTest {

        @Autowired
        private ProductRepository productRepository;

        @BeforeEach
        void setUp() {
                cleanupDatabase();
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
                TenantContext.setTenantId(tenant2Id);
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

                // Negative price -> FAIL (Bean Validation or DB)
                Product p1 = Product.builder()
                                .tenantId(tenantId)
                                .name("Bad Price")
                                .sku("SKU-BAD-P")
                                .salePrice(new BigDecimal("-1.0"))
                                .isDeleted(false)
                                .build();
                assertThrows(Exception.class, () -> productRepository.save(p1));

                // Negative stock -> FAIL (Bean Validation or DB)
                Product p2 = Product.builder()
                                .tenantId(tenantId)
                                .name("Bad Stock")
                                .sku("SKU-BAD-S")
                                .salePrice(BigDecimal.TEN)
                                .stock(-5)
                                .isDeleted(false)
                                .build();
                assertThrows(Exception.class, () -> productRepository.save(p2));
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

                var results = productRepository.searchFast(1L, "laptop", PageRequest.of(0, 10));
                assertEquals(1, results.getContent().size());
                assertEquals("Special Laptop", results.getContent().get(0).getName());

                results = productRepository.searchFast(1L, "gaming", PageRequest.of(0, 10));
                assertEquals(1, results.getContent().size());
                assertEquals("Special Laptop", results.getContent().get(0).getName());
        }

        @Test
        void testTenantIsolation() {
                Long tenantA = 1L;
                Long tenantB = 2L;

                TenantContext.setTenantId(tenantA);
                Product productA = Product.builder()
                                .tenantId(tenantA)
                                .name("Tenant A Product")
                                .sku("A-001")
                                .salePrice(BigDecimal.TEN)
                                .isDeleted(false)
                                .build();
                productRepository.save(productA);

                TenantContext.setTenantId(tenantB);
                Product productB = Product.builder()
                                .tenantId(tenantB)
                                .name("Tenant B Product")
                                .sku("B-001")
                                .salePrice(BigDecimal.TEN)
                                .isDeleted(false)
                                .build();
                productRepository.save(productB);

                TenantContext.setTenantId(tenantA);
                var tenantAProducts = productRepository.findByTenantIdAndIsDeletedFalse(tenantA, PageRequest.of(0, 10));

                assertEquals(1, tenantAProducts.getContent().size());
                assertEquals("Tenant A Product", tenantAProducts.getContent().get(0).getName());

                TenantContext.setTenantId(tenantB);
                var tenantBProducts = productRepository.findByTenantIdAndIsDeletedFalse(tenantB, PageRequest.of(0, 10));

                assertEquals(1, tenantBProducts.getContent().size());
                assertEquals("Tenant B Product", tenantBProducts.getContent().get(0).getName());
        }

        @Test
        void testOptimisticLockingRejectsStaleUpdates() {
                Long tenantId = 1L;
                TenantContext.setTenantId(tenantId);

                Product product = Product.builder()
                                .tenantId(tenantId)
                                .name("Versioned Product")
                                .sku("VER-001")
                                .salePrice(BigDecimal.TEN)
                                .isDeleted(false)
                                .build();
                product = productRepository.save(product);

                Product staleCopy = productRepository.findById(product.getId()).orElseThrow();
                assertEquals(0L, staleCopy.getVersion());

                Product freshCopy = productRepository.findById(product.getId()).orElseThrow();
                freshCopy.setName("Updated Name");
                productRepository.save(freshCopy);

                staleCopy.setName("Stale Update");
                assertThrows(
                                org.springframework.orm.ObjectOptimisticLockingFailureException.class,
                                () -> productRepository.save(staleCopy));
        }

        @Test
        void testReorderLevelConstraint() {
                Long tenantId = 1L;
                TenantContext.setTenantId(tenantId);

                Product p = Product.builder()
                                .tenantId(tenantId)
                                .name("Bad Reorder")
                                .sku("REORD-BAD")
                                .salePrice(BigDecimal.TEN)
                                .reorderLevel(-1)
                                .isDeleted(false)
                                .build();
                assertThrows(Exception.class, () -> productRepository.save(p));
        }
}
