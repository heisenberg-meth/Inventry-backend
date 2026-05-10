package com.ims.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ims.BaseIntegrationTest;
import com.ims.product.Product;
import com.ims.product.ProductRepository;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.exception.InsufficientStockException;
import com.ims.tenant.service.StockService;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
class StockConcurrencyIntegrationTest extends BaseIntegrationTest {

  @Autowired private StockService stockService;

  @Autowired private ProductRepository productRepository;

  private Product testProduct;

  @BeforeEach
  void setup() {

    TenantContext.setTenantId(testTenant1Id);

    testProduct =
        Product.builder()
            .name("Concurrency Test Product")
            .sku("CONC-001")
            .stock(5)
            .reorderLevel(2)
            .salePrice(BigDecimal.TEN)
            .purchasePrice(BigDecimal.ONE)
            .build();
    testProduct.setTenantId(testTenant1Id);
    testProduct = productRepository.save(testProduct);
  }

  @Test
  @DisplayName("Concurrent stockOut with limited stock: only one succeeds, never negative")
  void concurrentStockOutNeverGoesNegative() throws Exception {
    int initialStock = 5;
    int requestQty = 5;
    int threadCount = 4;

    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger();
    AtomicInteger failCount = new AtomicInteger();

    for (int i = 0; i < threadCount; i++) {
      executor.submit(
          () -> {
            try {
              startLatch.await();
              TenantContext.setTenantId(testTenant1Id);
              try {
                stockService.stockOut(
                    testProduct.getId(), requestQty, "Concurrent test", testUserId);
                successCount.incrementAndGet();
              } catch (InsufficientStockException e) {
                failCount.incrementAndGet();
              } catch (Exception e) {
                failCount.incrementAndGet();
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    doneLatch.await(30, TimeUnit.SECONDS);
    executor.shutdown();
    executor.awaitTermination(10, TimeUnit.SECONDS);

    Product after = productRepository.findById(testProduct.getId()).orElseThrow();

    assertThat(after.getStock())
        .isGreaterThanOrEqualTo(0)
        .as("Stock must never be negative under concurrent access");
    assertThat(successCount.get())
        .isEqualTo(1)
        .as(
            "Exactly one stockOut should succeed when stock=%d and each requests %d",
            initialStock, requestQty);
    assertThat(failCount.get())
        .isEqualTo(threadCount - 1)
        .as("Remaining %d concurrent requests should fail", threadCount - 1);
  }

  @Test
  @DisplayName("Sequential stockOut reduces stock correctly")
  void sequentialStockOutReducesCorrectly() {
    int initialStock = 10;
    int requestQty = 3;

    // Ensure product has enough stock for this test
    testProduct.setStock(initialStock);
    productRepository.save(testProduct);

    for (int i = 0; i < 3; i++) {
      stockService.stockOut(testProduct.getId(), requestQty, "Sequential test", testUserId);
    }

    Product after = productRepository.findById(testProduct.getId()).orElseThrow();
    assertThat(after.getStock()).isEqualTo(initialStock - (requestQty * 3));
  }

  @Test
  @DisplayName("StockOut exceeds available stock throws InsufficientStockException")
  void stockOutExceedsStockThrowsException() {
    assertThatThrownBy(
            () -> stockService.stockOut(testProduct.getId(), 999, "Over-stock test", testUserId))
        .isInstanceOf(InsufficientStockException.class)
        .hasMessageContaining("Insufficient stock");

    Product after = productRepository.findById(testProduct.getId()).orElseThrow();
    assertThat(after.getStock()).isEqualTo(5);
  }
}
