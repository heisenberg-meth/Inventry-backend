package com.ims.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.ims.model.Product;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.exception.InsufficientStockException;
import com.ims.tenant.repository.ProductRepository;
import com.ims.tenant.service.StockService;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.Objects;

@SpringBootTest
@ActiveProfiles("test")
public class StockConcurrencyIntegrationTest {

  @Autowired private StockService stockService;
  @Autowired private ProductRepository productRepository;

  private Long productId;

  @BeforeEach
  void setup() {
    TenantContext.set(1L);
    Product product = Product.builder()
        .tenantId(1L)
        .name("Concurrency Test Product")
        .sku("CONC-001")
        .salePrice(BigDecimal.valueOf(10.0))
        .stock(100)
        .reorderLevel(10)
        .isActive(true)
        .build();
    product = productRepository.save(Objects.requireNonNull(product));
    productId = product.getId();
    TenantContext.clear();
  }

  @AfterEach
  void tearDown() {
    TenantContext.set(1L);
    productRepository.deleteAll();
    TenantContext.clear();
  }

  @Test
  void testConcurrentStockOut() throws InterruptedException {
    int numberOfThreads = 20;
    int stockOutPerThread = 5; // Total 100 stock
    ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
    CountDownLatch latch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);
    AtomicInteger successfulCalls = new AtomicInteger(0);
    AtomicInteger failedCalls = new AtomicInteger(0);

    for (int i = 0; i < numberOfThreads; i++) {
      executor.execute(() -> {
        try {
          TenantContext.set(1L);
          latch.await();
          stockService.stockOut(productId, stockOutPerThread, "Concurrent test", 1L);
          successfulCalls.incrementAndGet();
        } catch (InsufficientStockException e) {
          failedCalls.incrementAndGet();
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          TenantContext.clear();
          doneLatch.countDown();
        }
      });
    }

    latch.countDown(); // Start all threads simultaneously
    doneLatch.await(10, TimeUnit.SECONDS);
    executor.shutdown();

    TenantContext.set(1L);
    Product finalProduct = productRepository.findById(productId).orElseThrow();
    assertThat(finalProduct.getStock()).isEqualTo(0);
    assertThat(successfulCalls.get()).isEqualTo(20);
    assertThat(failedCalls.get()).isEqualTo(0);
  }

  @Test
  void testConcurrentStockOutOversell() throws InterruptedException {
    int numberOfThreads = 10;
    int stockOutPerThread = 15; // Total 150 stock requested, only 100 available
    ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
    CountDownLatch latch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);
    AtomicInteger successfulCalls = new AtomicInteger(0);
    AtomicInteger failedCalls = new AtomicInteger(0);

    for (int i = 0; i < numberOfThreads; i++) {
      executor.execute(() -> {
        try {
          TenantContext.set(1L);
          latch.await();
          stockService.stockOut(productId, stockOutPerThread, "Concurrent oversell test", 1L);
          successfulCalls.incrementAndGet();
        } catch (InsufficientStockException e) {
          failedCalls.incrementAndGet();
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          TenantContext.clear();
          doneLatch.countDown();
        }
      });
    }

    latch.countDown();
    doneLatch.await(10, TimeUnit.SECONDS);
    executor.shutdown();

    TenantContext.set(1L);
    Product finalProduct = productRepository.findById(productId).orElseThrow();
    
    // 100 / 15 = 6.666. So 6 calls should succeed, 4 should fail.
    assertThat(successfulCalls.get()).isEqualTo(6);
    assertThat(failedCalls.get()).isEqualTo(4);
    assertThat(finalProduct.getStock()).isEqualTo(10); // 100 - 90
  }
}
