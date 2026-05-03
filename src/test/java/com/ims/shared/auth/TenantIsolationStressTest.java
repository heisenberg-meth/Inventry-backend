package com.ims.shared.auth;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import com.ims.BaseIntegrationTest;
import com.ims.dto.response.PagedResponse;
import com.ims.dto.response.ProductResponse;
import com.ims.product.Product;
import com.ims.model.Role;
import com.ims.model.User;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * HARD TEST: Verifies absolute tenant isolation under high concurrency.
 * Ensures that even with simultaneous requests, data from one tenant
 * never leaks into another.
 */
class TenantIsolationStressTest extends BaseIntegrationTest {

  private String token1;
  private String token2;
  private final int THREAD_COUNT = 20;
  private final int ITERATIONS_PER_THREAD = 10;

  @Override
  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    cleanupDatabase();

    // Setup Tenant 1 - Set context first
    TenantContext.setTenantId(testTenant1Id);
    String pass = passwordEncoder.encode("password");
    Role adminRole1 = getOrCreateRole("TENANT_ADMIN", testTenant1Id);
    seedTenantPermissions(adminRole1.getId());
    userRepository.save(User.builder()
        .name("Admin 1")
        .email("admin1@t1.com")
        .passwordHash(pass)
        .role(adminRole1)
        .scope("TENANT")
        .isVerified(true)
        .isActive(true)
        .build());
    verifyUser("admin1@t1.com");
    token1 = login("admin1@t1.com", "password", "T1001", testTenant1Id);

    // Set context for Tenant 2
    TenantContext.setTenantId(testTenant2Id);
    Role adminRole2 = getOrCreateRole("TENANT_ADMIN", testTenant2Id);
    seedTenantPermissions(adminRole2.getId());
    userRepository.save(User.builder()
        .name("Admin 2")
        .email("admin2@t2.com")
        .passwordHash(pass)
        .role(adminRole2)
        .scope("TENANT")
        .isVerified(true)
        .isActive(true)
        .build());
    verifyUser("admin2@t2.com");
    token2 = login("admin2@t2.com", "password", "T2001", testTenant2Id);

    // Create unique products for each tenant
    TenantContext.setTenantId(testTenant1Id);
    for (int i = 0; i < 5; i++) {
      productRepository.save(Product.builder()
          .name("T1-Product-" + i)
          .sku("T1-SKU-" + i)
          .tenantId(testTenant1Id)
          .salePrice(BigDecimal.valueOf(100))
          .stock(10)
          .active(true)
          .build());
    }

    TenantContext.setTenantId(testTenant2Id);
    for (int i = 0; i < 5; i++) {
      productRepository.save(Product.builder()
          .name("T2-Product-" + i)
          .sku("T2-SKU-" + i)
          .tenantId(testTenant2Id)
          .salePrice(BigDecimal.valueOf(200))
          .stock(20)
          .active(true)
          .build());
    }
    TenantContext.clear();
  }

  @Test
  void testConcurrentCrossTenantIsolation() throws InterruptedException {
    ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
    CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
    AtomicInteger leaksDetected = new AtomicInteger(0);
    AtomicInteger totalRequests = new AtomicInteger(0);
    AtomicInteger errors = new AtomicInteger(0);

    for (int i = 0; i < THREAD_COUNT; i++) {
      final int threadNum = i;
      executor.execute(() -> {
        try {
          for (int j = 0; j < ITERATIONS_PER_THREAD; j++) {
            boolean useTenant1 = (threadNum + j) % 2 == 0;
            String currentToken = useTenant1 ? token1 : token2;
            String forbiddenPrefix = useTenant1 ? "T2-" : "T1-";

            var mvcResult = mockMvc.perform(get("/api/v1/tenant/products")
                .header("Authorization", "Bearer " + currentToken)
                .with(tenant(useTenant1 ? testTenant1Id : testTenant2Id))
                .contentType(MediaType.APPLICATION_JSON))
                .andReturn();

            int status = mvcResult.getResponse().getStatus();
            if (status != 200) {
              errors.incrementAndGet();
              System.err.println("Thread " + threadNum + " got status " + status + " body: "
                  + mvcResult.getResponse().getContentAsString());
              continue;
            }

            String resultJson = mvcResult.getResponse().getContentAsString();
            PagedResponse<ProductResponse> response = objectMapper.readValue(resultJson,
                objectMapper.getTypeFactory().constructParametricType(PagedResponse.class, ProductResponse.class));

            for (ProductResponse p : response.getContent()) {
              if (p.getName().startsWith(forbiddenPrefix)) {
                leaksDetected.incrementAndGet();
              }
            }
            totalRequests.incrementAndGet();
          }
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          latch.countDown();
        }
      });
    }

    latch.await();
    executor.shutdown();

    System.out.println("Stress test completed. Total requests: " + totalRequests.get() + ", Errors: " + errors.get());
    assertTrue(totalRequests.get() > 0, "No requests were executed. Errors: " + errors.get());
    assertTrue(leaksDetected.get() == 0,
        "DATA LEAK DETECTED! " + leaksDetected.get() + " cross-tenant items found in responses.");
  }

  private void seedTenantPermissions(Long roleId) {
    new org.springframework.transaction.support.TransactionTemplate(
        java.util.Objects.requireNonNull(transactionManager)).execute(status -> {
          jdbcTemplate.execute(
              "INSERT INTO role_permissions (role_id, permission_id) " +
                  "SELECT " + roleId + ", p.id FROM permissions p " +
                  "WHERE p.key IN ('view_products', 'view_product', 'create_product', 'update_product', 'delete_product') "
                  +
                  "ON CONFLICT DO NOTHING");
          return null;
        });
  }
}
