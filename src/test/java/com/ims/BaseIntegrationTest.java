package com.ims;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.category.CategoryRepository;
import com.ims.dto.request.SignupRequest;
import com.ims.platform.repository.SubscriptionPlanRepository;
import com.ims.platform.repository.SubscriptionRepository;
import com.ims.platform.repository.SystemConfigRepository;
import com.ims.platform.repository.TenantRepository;
import com.ims.product.ProductRepository;
import com.ims.shared.audit.AuditLogRepository;
import com.ims.helper.AuthTestHelper;
import com.ims.helper.DatabaseCleanupHelper;
import com.ims.helper.ProductTestHelper;
import com.ims.helper.TenantTestHelper;
import com.ims.helper.TestDataFactory;
import com.ims.helper.SecurityTestUtils;
import com.ims.tenant.repository.CustomerRepository;
import com.ims.tenant.repository.InventoryRepository;
import com.ims.tenant.repository.InvoiceRepository;
import com.ims.tenant.repository.OrderItemRepository;
import com.ims.tenant.repository.OrderRepository;
import com.ims.tenant.repository.PaymentRepository;
import com.ims.tenant.repository.RoleRepository;
import com.ims.tenant.repository.StockMovementRepository;
import com.ims.tenant.repository.SupplierRepository;
import com.ims.tenant.repository.SupportAttachmentRepository;
import com.ims.tenant.repository.SupportMessageRepository;
import com.ims.tenant.repository.SupportTicketRepository;
import com.ims.tenant.repository.TransferOrderRepository;
import com.ims.tenant.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest(classes = ImsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(BaseIntegrationTest.FlywayTestConfig.class)
public abstract class BaseIntegrationTest {

  @TestConfiguration
  static class FlywayTestConfig {
    @Bean
    public FlywayMigrationStrategy cleanMigrateStrategy() {
      return flyway -> {
        flyway.repair();
        flyway.migrate();
      };
    }
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    // Default to external containers on ports 5433/6379
    final String postgresHost = "localhost";
    final int postgresPort = 5433;
    final String redisHost = "localhost";
    final int redisPort = 6379;

    // Allow environment variables to override
    String envPostgres = System.getenv("TESTCONTAINERS_POSTGRES_URL");
    String envRedis = System.getenv("TESTCONTAINERS_REDIS_HOST");
    if (envPostgres != null && !envPostgres.isBlank()) {
      String[] parts = envPostgres.split(":");
      if (parts.length >= 3) {
        String host = parts[1].replace("//", "");
        int port = postgresPort;
        try {
          String portStr = parts[2].split("/")[0];
          port = Integer.parseInt(portStr);
        } catch (NumberFormatException expected) {
          // Use default
        }
        final String jdbcUrl = String.format("jdbc:postgresql://%s:%d/ims_db", host, port);
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", () -> "ims_user");
        registry.add("spring.datasource.password", () -> "changeme");
      } else {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5433/ims_db");
        registry.add("spring.datasource.username", () -> "ims_user");
        registry.add("spring.datasource.password", () -> "changeme");
      }
    } else {
      final String jdbcUrl = String.format("jdbc:postgresql://%s:%d/ims_db", postgresHost, postgresPort);
      registry.add("spring.datasource.url", () -> jdbcUrl);
      registry.add("spring.datasource.username", () -> "ims_user");
      registry.add("spring.datasource.password", () -> "changeme");
    }

    if (envRedis != null && !envRedis.isBlank()) {
      final String host = envRedis;
      registry.add("spring.data.redis.host", () -> host);
      registry.add("spring.data.redis.port", () -> 6379);
    } else {
      registry.add("spring.data.redis.host", () -> redisHost);
      registry.add("spring.data.redis.port", () -> redisPort);
    }

    registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    registry.add("spring.flyway.enabled", () -> "true");
  }

  @AfterEach
  void clearTenantContext() {
    tenantTestHelper.clear();
  }

  @Autowired
  protected TenantRepository tenantRepository;
  @Autowired
  protected UserRepository userRepository;
  @Autowired
  protected RoleRepository roleRepository;
  @Autowired
  protected CustomerRepository customerRepository;
  @Autowired
  protected SupplierRepository supplierRepository;
  @Autowired
  protected ProductRepository productRepository;
  @Autowired
  protected CategoryRepository categoryRepository;
  @Autowired
  protected OrderRepository orderRepository;
  @Autowired
  protected OrderItemRepository orderItemRepository;
  @Autowired
  protected InventoryRepository inventoryRepository;
  @Autowired
  protected StockMovementRepository stockMovementRepository;
  @Autowired
  protected InvoiceRepository invoiceRepository;
  @Autowired
  protected AuditLogRepository auditLogRepository;
  @Autowired
  protected PaymentRepository paymentRepository;
  @Autowired
  protected TransferOrderRepository transferOrderRepository;
  @Autowired
  protected SubscriptionRepository subscriptionRepository;
  @Autowired
  protected SubscriptionPlanRepository subscriptionPlanRepository;
  @Autowired
  protected SupportAttachmentRepository supportAttachmentRepository;
  @Autowired
  protected SupportMessageRepository supportMessageRepository;
  @Autowired
  protected SupportTicketRepository supportTicketRepository;
  @Autowired
  protected SystemConfigRepository systemConfigRepository;

  @Autowired
  protected EntityManager entityManager;
  @Autowired
  protected JdbcTemplate jdbcTemplate;
  @Autowired
  protected PasswordEncoder passwordEncoder;

  @PersistenceContext
  protected EntityManager em;

  @Autowired
  protected PlatformTransactionManager transactionManager;
  @Autowired
  protected TransactionTemplate transactionTemplate;

  protected Long testTenant1Id;
  protected Long testTenant2Id;
  protected Long testUserId;
  protected String testUserToken;
  @Autowired
  protected RedisTemplate<String, Object> redisTemplate;
  @Autowired
  protected MockMvc mockMvc;
  @Autowired
  protected ObjectMapper objectMapper;
  @Autowired
  protected AuthTestHelper authTestHelper;
  @Autowired
  protected TenantTestHelper tenantTestHelper;
  @Autowired
  protected DatabaseCleanupHelper databaseCleanupHelper;
  @Autowired
  protected ProductTestHelper productTestHelper;
  @Autowired
  protected TestDataFactory testDataFactory;

  protected String login(String email, String password, String companyCode) throws Exception {
    return authTestHelper.login(email, password, companyCode);
  }

  protected SignupRequest createSignupRequest(
      String name, String slug, String email) {
    return authTestHelper.createSignupRequest(name, slug, email);
  }

  @BeforeEach
  void baseSetUp() {
    cleanupDatabase();

    // Create fresh tenants for each test
    var tenant1 = testDataFactory.createTenant();
    testTenant1Id = tenant1.getId();

    var tenant2 = testDataFactory.createTenant();
    testTenant2Id = tenant2.getId();

    // Create a fresh user for tenant 1
    var user = testDataFactory.createUser(tenant1);
    testUserId = user.getId();

    // Initialize contexts for tenant 1 by default
    testDataFactory.initializeTenantContext(tenant1);
    SecurityTestUtils.setAuthenticatedUser(user);

    // Get a real token for MockMvc calls
    try {
      testUserToken = authTestHelper.login(user.getEmail(), "password123", tenant1.getCompanyCode());
    } catch (Exception e) {
      log.error("Failed to generate test token", e);
    }
  }

  protected void cleanupDatabase() {
    databaseCleanupHelper.cleanup();
  }

  protected void withTenant(Long tenantId, Runnable action) {
    tenantTestHelper.withTenant(tenantId, action);
  }

  protected <T> T withTenant(Long tenantId, Supplier<T> action) {
    return tenantTestHelper.withTenant(tenantId, action);
  }

  protected void verifyUser(String email) {
    jdbcTemplate.execute("UPDATE users SET is_verified = true WHERE email = '" + email + "'");
  }
}
