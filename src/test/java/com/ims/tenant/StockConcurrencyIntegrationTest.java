package com.ims.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import com.ims.BaseIntegrationTest;
import com.ims.dto.request.SignupRequest;
import com.ims.product.Product;
import com.ims.shared.auth.SignupService;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.exception.InsufficientStockException;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.Objects;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
    "spring.cache.type=none"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class StockConcurrencyIntegrationTest extends BaseIntegrationTest {

  @Autowired
  private SignupService signupService;
  @Autowired
  private StockService stockService;

  private long productId;
  private long userId;
  private long tenantId;

  @BeforeEach
  void setup() throws Exception {
    cleanupDatabase();

    SignupRequest signup = new SignupRequest();
    signup.setBusinessName("Conc Corp");
    signup.setWorkspaceSlug("conc-corp");
    signup.setBusinessType("RETAIL");
    signup.setOwnerName("Admin");
    signup.setOwnerEmail("admin@conc.com");
    signup.setPassword("password123");
    signupService.signup(signup);

    // Query directly via JDBC to avoid transaction lag or cache issues in setup
    tenantId = Objects.requireNonNull(
        jdbcTemplate.queryForObject("SELECT id FROM tenants WHERE workspace_slug = 'conc-corp'", Long.class));
    userId = Objects
        .requireNonNull(jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = 'admin@conc.com'", Long.class));
    verifyUser("admin@conc.com");

    TenantContext.setTenantId(tenantId);
    Product product = Product.builder()
        .tenantId(tenantId)
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
          TenantContext.setTenantId(tenantId);
          latch.await();
          stockService.stockOut(productId, stockOutPerThread, "Concurrent test", userId);
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

    TenantContext.setTenantId(tenantId);
    Product finalProduct = productRepository.findById(productId).orElseThrow();
    assertThat(finalProduct.getStock()).isEqualTo(0);
    assertThat(successfulCalls.get()).isEqualTo(20);
    TenantContext.clear();
  }
}
