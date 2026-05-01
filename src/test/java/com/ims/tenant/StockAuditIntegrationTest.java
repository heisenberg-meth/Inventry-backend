package com.ims.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.ims.BaseIntegrationTest;
import com.ims.model.StockMovement;
import com.ims.model.User;
import com.ims.model.UserRole;
import com.ims.product.Product;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.service.StockService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

public class StockAuditIntegrationTest extends BaseIntegrationTest {

  @Autowired
  private StockService stockService;

  private Long product1Id;
  private Long product2Id;
  private Long user1Id;
  private Long user2Id;

  @BeforeEach
  void setup() {
    cleanupDatabase();
    mockRedisAndCache();

    // Tenant 1
    TenantContext.setTenantId(testTenant1Id);
    User u1 = User.builder()
        .tenantId(testTenant1Id)
        .email("u1@t1.com")
        .name("U1")
        .passwordHash("p")
        .role(Objects.requireNonNull(getOrCreateRole(Objects.requireNonNull(UserRole.TENANT_ADMIN.name()), testTenant1Id)))
        .scope("TENANT")
        .isVerified(true)
        .build();
    u1 = userRepository.save(Objects.requireNonNull(u1));
    user1Id = u1.getId();

    Product p1 = Product.builder()
        .tenantId(testTenant1Id)
        .name("T1 Product")
        .sku("T1-PROD")
        .salePrice(Objects.requireNonNull(BigDecimal.valueOf(10.0)))
        .stock(50)
        .reorderLevel(5)
        .active(true)
        .build();
    p1 = productRepository.save(Objects.requireNonNull(p1));
    product1Id = p1.getId();

    // Tenant 2
    TenantContext.setTenantId(testTenant2Id);
    User u2 = User.builder()
        .tenantId(testTenant2Id)
        .email("u2@t2.com")
        .name("U2")
        .passwordHash("p")
        .role(Objects.requireNonNull(getOrCreateRole(Objects.requireNonNull(UserRole.TENANT_ADMIN.name()), testTenant2Id)))
        .scope("TENANT")
        .isVerified(true)
        .build();
    u2 = userRepository.save(Objects.requireNonNull(u2));
    user2Id = u2.getId();

    Product p2 = Product.builder()
        .tenantId(testTenant2Id)
        .name("T2 Product")
        .sku("T2-PROD")
        .salePrice(Objects.requireNonNull(BigDecimal.valueOf(20.0)))
        .stock(100)
        .reorderLevel(10)
        .active(true)
        .build();
    p2 = productRepository.save(Objects.requireNonNull(p2));
    product2Id = p2.getId();

    TenantContext.clear();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void testStockAuditTrail() {
    TenantContext.setTenantId(testTenant1Id);

    // 1. Stock In
    stockService.stockIn(Objects.requireNonNull(product1Id), 10, "Initial stock", Objects.requireNonNull(user1Id));

    // 2. Stock Out
    stockService.stockOut(Objects.requireNonNull(product1Id), 5, "Sale", Objects.requireNonNull(user1Id));

    // 3. Stock Adjust
    stockService.stockAdjust(Objects.requireNonNull(product1Id), -2, "Damage", Objects.requireNonNull(user1Id));

    Page<StockMovement> movements = stockService.getMovements(PageRequest.of(0, 10));
    List<StockMovement> list = movements.getContent();

    assertThat(list).hasSize(3);

    // Most recent first
    assertThat(list.get(0).getMovementType()).isEqualTo("ADJUSTMENT");
    assertThat(list.get(0).getPreviousStock()).isEqualTo(55);
    assertThat(list.get(0).getNewStock()).isEqualTo(53);

    assertThat(list.get(1).getMovementType()).isEqualTo("OUT");
    assertThat(list.get(1).getPreviousStock()).isEqualTo(60);
    assertThat(list.get(1).getNewStock()).isEqualTo(55);

    assertThat(list.get(2).getMovementType()).isEqualTo("IN");
    assertThat(list.get(2).getPreviousStock()).isEqualTo(50);
    assertThat(list.get(2).getNewStock()).isEqualTo(60);
  }

  @Test
  void testMultiTenantIsolation() {
    // Tenant 1 actions
    TenantContext.setTenantId(testTenant1Id);
    stockService.stockIn(Objects.requireNonNull(product1Id), 10, "T1 In", Objects.requireNonNull(user1Id));

    // Tenant 2 actions
    TenantContext.setTenantId(testTenant2Id);
    stockService.stockIn(Objects.requireNonNull(product2Id), 50, "T2 In", Objects.requireNonNull(user2Id));
    stockService.stockOut(Objects.requireNonNull(product2Id), 5, "T2 Out", Objects.requireNonNull(user2Id));

    // Verify Tenant 1 only sees their movements
    TenantContext.setTenantId(testTenant1Id);
    Page<StockMovement> t1Movements = stockService.getMovements(PageRequest.of(0, 10));
    assertThat(t1Movements.getContent()).hasSize(1);
    assertThat(t1Movements.getContent().get(0).getNotes()).isEqualTo("T1 In");

    // Verify Tenant 2 only sees their movements
    TenantContext.setTenantId(testTenant2Id);
    Page<StockMovement> t2Movements = stockService.getMovements(PageRequest.of(0, 10));
    assertThat(t2Movements.getContent()).hasSize(2);
    assertThat(t2Movements.getContent().stream().anyMatch(m -> m.getNotes().equals("T2 In")))
        .isTrue();
    assertThat(t2Movements.getContent().stream().anyMatch(m -> m.getNotes().equals("T2 Out")))
        .isTrue();
  }

  @Test
  void testLowStockAlerts() {
    TenantContext.setTenantId(testTenant1Id);

    // Initial stock is 50, reorder level is 5.
    // Stock out 46 -> stock is 4 (Low Stock!)
    stockService.stockOut(Objects.requireNonNull(product1Id), 46, "Big sale", Objects.requireNonNull(user1Id));

    var lowStock = productRepository.findLowStock();
    assertThat(lowStock).hasSize(1);
    assertThat(lowStock.get(0).getId()).isEqualTo(product1Id);

    // Stock in 10 -> stock is 14 (Not low stock anymore)
    stockService.stockIn(Objects.requireNonNull(product1Id), 10, "Restock", Objects.requireNonNull(user1Id));
    lowStock = productRepository.findLowStock();
    assertThat(lowStock).isEmpty();
  }
}
