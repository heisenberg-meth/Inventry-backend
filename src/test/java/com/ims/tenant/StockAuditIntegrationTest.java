package com.ims.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.ims.model.Product;
import com.ims.model.StockMovement;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.repository.ProductRepository;
import com.ims.tenant.repository.StockMovementRepository;
import com.ims.tenant.service.StockService;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import java.util.Objects;
import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
public class StockAuditIntegrationTest {

  @Autowired private StockService stockService;
  @Autowired private ProductRepository productRepository;
  @Autowired private StockMovementRepository stockMovementRepository;

  private Long product1Id;
  private Long product2Id;

  @BeforeEach
  void setup() {
    // Tenant 1
    TenantContext.set(1L);
    Product p1 = Product.builder()
        .tenantId(1L)
        .name("T1 Product")
        .sku("T1-PROD")
        .salePrice(BigDecimal.valueOf(10.0))
        .stock(50)
        .reorderLevel(5)
        .isActive(true)
        .build();
    p1 = productRepository.save(Objects.requireNonNull(p1));
    product1Id = p1.getId();

    // Tenant 2
    TenantContext.set(2L);
    Product p2 = Product.builder()
        .tenantId(2L)
        .name("T2 Product")
        .sku("T2-PROD")
        .salePrice(BigDecimal.valueOf(20.0))
        .stock(100)
        .reorderLevel(10)
        .isActive(true)
        .build();
    p2 = productRepository.save(Objects.requireNonNull(p2));
    product2Id = p2.getId();
    
    TenantContext.clear();
  }

  @AfterEach
  void tearDown() {
    TenantContext.set(1L);
    stockMovementRepository.deleteAll();
    productRepository.deleteAll();
    TenantContext.set(2L);
    stockMovementRepository.deleteAll();
    productRepository.deleteAll();
    TenantContext.clear();
  }

  @Test
  void testStockAuditTrail() {
    TenantContext.set(1L);
    
    // 1. Stock In
    stockService.stockIn(product1Id, 10, "Initial stock", 1L);
    
    // 2. Stock Out
    stockService.stockOut(product1Id, 5, "Sale", 1L);
    
    // 3. Stock Adjust
    stockService.stockAdjust(product1Id, -2, "Damage", 1L);

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
    TenantContext.set(1L);
    stockService.stockIn(product1Id, 10, "T1 In", 1L);
    
    // Tenant 2 actions
    TenantContext.set(2L);
    stockService.stockIn(product2Id, 50, "T2 In", 2L);
    stockService.stockOut(product2Id, 5, "T2 Out", 2L);

    // Verify Tenant 1 only sees their movements
    TenantContext.set(1L);
    Page<StockMovement> t1Movements = stockService.getMovements(PageRequest.of(0, 10));
    assertThat(t1Movements.getContent()).hasSize(1);
    assertThat(t1Movements.getContent().get(0).getNotes()).isEqualTo("T1 In");

    // Verify Tenant 2 only sees their movements
    TenantContext.set(2L);
    Page<StockMovement> t2Movements = stockService.getMovements(PageRequest.of(0, 10));
    assertThat(t2Movements.getContent()).hasSize(2);
    assertThat(t2Movements.getContent().stream().anyMatch(m -> m.getNotes().equals("T2 In"))).isTrue();
    assertThat(t2Movements.getContent().stream().anyMatch(m -> m.getNotes().equals("T2 Out"))).isTrue();
  }

  @Test
  void testLowStockAlerts() {
    TenantContext.set(1L);
    
    // Initial stock is 50, reorder level is 5.
    // Stock out 46 -> stock is 4 (Low Stock!)
    stockService.stockOut(product1Id, 46, "Big sale", 1L);
    
    var lowStock = productRepository.findLowStock();
    assertThat(lowStock).hasSize(1);
    assertThat(lowStock.get(0).getId()).isEqualTo(product1Id);
    
    // Stock in 10 -> stock is 14 (Not low stock anymore)
    stockService.stockIn(product1Id, 10, "Restock", 1L);
    lowStock = productRepository.findLowStock();
    assertThat(lowStock).isEmpty();
  }
}
