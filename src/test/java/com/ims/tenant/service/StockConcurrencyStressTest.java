package com.ims.tenant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.ims.BaseIntegrationTest;
import com.ims.product.Product;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.exception.InsufficientStockException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * HARD TEST: Verifies stock integrity under extreme concurrency.
 * Ensures that multiple threads competing for the last items of a product
 * result in a consistent zero-stock state with no over-selling.
 */
class StockConcurrencyStressTest extends BaseIntegrationTest {

  @Autowired private StockService stockService;

  private Long productId;
  private final int INITIAL_STOCK = 10;
  private final int THREAD_COUNT = 50;

  @BeforeEach
  void setup() {
    cleanupDatabase();
    TenantContext.setTenantId(testTenant1Id);
    
    Product p = productRepository.save(Product.builder()
        .name("Race-Condition-Product")
        .sku("RACE-001")
        .tenantId(testTenant1Id)
        .salePrice(BigDecimal.valueOf(100))
        .stock(INITIAL_STOCK)
        .isActive(true)
        .build());
    productId = p.getId();
    TenantContext.clear();
  }

  @Test
  void testConcurrentStockReduction() throws InterruptedException {
    ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
    CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
    
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failureCount = new AtomicInteger(0);
    List<String> errors = new ArrayList<>();

    for (int i = 0; i < THREAD_COUNT; i++) {
      executor.execute(() -> {
        TenantContext.setTenantId(testTenant1Id);
        try {
          // Each thread tries to take 1 unit
          stockService.stockOut(productId, 1, "Stress Test Reduction", 1L);
          successCount.incrementAndGet();
        } catch (InsufficientStockException e) {
          failureCount.incrementAndGet();
        } catch (Exception e) {
          synchronized (errors) {
            errors.add(e.getMessage());
          }
        } finally {
          TenantContext.clear();
          latch.countDown();
        }
      });
    }

    latch.await();
    executor.shutdown();

    TenantContext.setTenantId(testTenant1Id);
    Product finalProduct = productRepository.findById(productId).orElseThrow();
    TenantContext.clear();

    System.out.println("Stress Test Results:");
    System.out.println("Initial Stock: " + INITIAL_STOCK);
    System.out.println("Success Count: " + successCount.get());
    System.out.println("Failure Count (Insufficient): " + failureCount.get());
    System.out.println("Final Stock: " + finalProduct.getStock());
    
    if (!errors.isEmpty()) {
      System.out.println("Unexpected Errors: " + errors);
    }

    assertEquals(0, errors.size(), "Should have no unexpected errors (only InsufficientStockException allowed)");
    assertEquals(INITIAL_STOCK, successCount.get(), "Should only allow successful reduction up to initial stock");
    assertEquals(THREAD_COUNT - INITIAL_STOCK, failureCount.get(), "Remaining threads should fail with InsufficientStockException");
    assertEquals(0, finalProduct.getStock(), "Final stock must be exactly zero");
  }
}
