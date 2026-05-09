package com.ims;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.category.CategoryRepository;
import com.ims.platform.repository.SubscriptionPlanRepository;
import com.ims.platform.repository.SubscriptionRepository;
import com.ims.platform.repository.SystemConfigRepository;
import com.ims.platform.repository.TenantRepository;
import com.ims.product.ProductRepository;
import com.ims.shared.audit.AuditLogRepository;
import com.ims.shared.auth.TenantContext;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(classes = com.ims.ImsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
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
      // Parse JDBC URL like jdbc:postgresql://host:port/db
      String[] parts = envPostgres.split(":");
      if (parts.length >= 3) {
        String host = parts[1].replace("//", "");
        int port = postgresPort;
        try {
          String portStr = parts[2].split("/")[0];
          port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
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
    TenantContext.clear();
    org.slf4j.MDC.remove("tenantId");
  }

  protected void withTenant(Long tenantId, Runnable action) {
    try {
      TenantContext.setTenantId(tenantId);
      org.slf4j.MDC.put("tenantId", String.valueOf(tenantId));
      action.run();
    } finally {
      TenantContext.clear();
      org.slf4j.MDC.remove("tenantId");
    }
  }

  protected <T> T withTenant(Long tenantId, Supplier<T> action) {
    try {
      TenantContext.setTenantId(tenantId);
      org.slf4j.MDC.put("tenantId", String.valueOf(tenantId));
      return action.get();
    } finally {
      TenantContext.clear();
      org.slf4j.MDC.remove("tenantId");
    }
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

  @Autowired
  protected RedisTemplate<String, Object> redisTemplate;
  @Autowired
  protected MockMvc mockMvc;
  @Autowired
  protected ObjectMapper objectMapper;

  protected String login(String email, String password, String companyCode) throws Exception {
    com.ims.dto.request.LoginRequest loginRequest = new com.ims.dto.request.LoginRequest();
    loginRequest.setEmail(email);
    loginRequest.setPassword(password);
    loginRequest.setCompanyCode(companyCode);

    String loginJson = objectMapper.writeValueAsString(loginRequest);
    MvcResult result = mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                "/api/auth/login")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(loginJson))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
        .andReturn();

    String responseJson = result.getResponse().getContentAsString();
    com.ims.dto.response.LoginResponse response = objectMapper.readValue(responseJson,
        com.ims.dto.response.LoginResponse.class);
    return response.getAccessToken();
  }

  protected com.ims.dto.request.SignupRequest createSignupRequest(
      String name, String slug, String email) {
    com.ims.dto.request.SignupRequest req = new com.ims.dto.request.SignupRequest();
    req.setBusinessName(name);
    req.setBusinessType("RETAIL");
    req.setOwnerName("Owner " + name);
    req.setOwnerEmail(email);
    req.setPassword("password123");
    req.setWorkspaceSlug(slug);
    return req;
  }

  @BeforeEach
  void baseSetUp() {
    TenantContext.setTenantId(1L);
    cleanupDatabase();
    // Leave the tenant context set for the tests to use by default.
    TenantContext.setTenantId(testTenant1Id);
  }

  protected void cleanupDatabase() {
    if (jdbcTemplate == null)
      return;

    String[] tables = {
        "order_items",
        "orders",
        "customers",
        "suppliers",
        "products",
        "categories",
        "users",
        "tenants",
        "roles",
        "permissions",
        "notifications",
        "alerts",
        "webhooks",
        "audit_logs",
        "subscriptions",
        "subscription_plans",
        "support_tickets",
        "support_messages",
        "support_attachments",
        "role_permissions",
        "stock_movements",
        "invoices",
        "payments",
        "outbox_event"
    };

    for (String table : tables) {
      try {
        jdbcTemplate.execute("TRUNCATE TABLE " + table + " RESTART IDENTITY CASCADE");
      } catch (Exception e) {
        // Table might not exist or be currently locked
      }
    }
    seedTestData();
  }

  protected void verifyUser(String email) throws Exception {
    if (jdbcTemplate != null) {
      jdbcTemplate.execute("UPDATE users SET is_verified = true WHERE email = '" + email + "'");
    }
  }

  protected void seedTestData() {
    if (jdbcTemplate == null) {
      return;
    }
    try {
      jdbcTemplate.execute(
          "INSERT INTO subscription_plans (name, price, max_users, max_products, "
              + "billing_cycle, created_at, updated_at, version) "
              + "VALUES ('FREE', 0.00, 3, 100, 'MONTHLY', NOW(), NOW(), 0)");
    } catch (Exception e) {
      // Ignored if already exists
    }
    try {
      jdbcTemplate.execute(
          "INSERT INTO tenants (name, workspace_slug, company_code, business_type, status, "
              + "is_active, created_at, updated_at, version) "
              + "VALUES ('Tenant 1', 't1', 'T1001', 'RETAIL', 'ACTIVE', true, NOW(), NOW(), 0)");
      jdbcTemplate.execute(
          "INSERT INTO tenants (name, workspace_slug, company_code, business_type, status, "
              + "is_active, created_at, updated_at, version) "
              + "VALUES ('Tenant 2', 't2', 'T2001', 'RETAIL', 'ACTIVE', true, NOW(), NOW(), 0)");
      testTenant1Id = jdbcTemplate.queryForObject(
          "SELECT id FROM tenants WHERE workspace_slug = 't1'", Long.class);
      testTenant2Id = jdbcTemplate.queryForObject(
          "SELECT id FROM tenants WHERE workspace_slug = 't2'", Long.class);
    } catch (Exception e) {
      // Ignored if already exists
    }
    try {
      jdbcTemplate.execute(
          "INSERT INTO roles (name) VALUES ('ADMIN'), ('MANAGER'), ('STAFF'), ('ROOT'), ('PLATFORM_ADMIN')");
    } catch (Exception e) {
      // Ignored if already exists
    }
    try {
      // Use passwordEncoder to ensure the hash matches "root123"
      String passwordHash = passwordEncoder.encode("root123");
      jdbcTemplate.execute(
          "INSERT INTO users (name, email, password_hash, role, scope, is_active, "
              + "is_verified, is_platform_user, tenant_id, created_at, updated_at, version) "
              + "VALUES ('Root Admin', 'root@test.com', '"
              + passwordHash
              + "', 'ROOT', 'PLATFORM', true, true, true, "
              + testTenant1Id
              + ", NOW(), NOW(), 0)");
      testUserId = jdbcTemplate.queryForObject(
          "SELECT id FROM users WHERE email = 'root@test.com'", Long.class);
    } catch (Exception e) {
      // Ignored if already exists
    }
    try {
      jdbcTemplate.execute(
          "INSERT INTO permissions (\"key\", description) VALUES "
              + "('read:products', 'Read Products'), ('write:products', 'Write Products'), "
              + "('read:orders', 'Read Orders'), ('write:orders', 'Write Orders')");
    } catch (Exception e) {
      // Ignored if already exists
    }
  }
}
