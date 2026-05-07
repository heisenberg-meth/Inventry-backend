package com.ims;

import com.ims.platform.repository.*;
import com.ims.shared.audit.AuditLogRepository;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.repository.*;
import com.ims.product.ProductRepository;
import com.ims.category.CategoryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = com.ims.ImsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
public abstract class BaseIntegrationTest {

  @Container
  @SuppressWarnings("resource")
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.4-alpine")
      .withDatabaseName("ims_db")
      .withUsername("ims_user")
      .withPassword("changeme");

  @Container
  @SuppressWarnings("resource")
  static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
      .withExposedPorts(6379);

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
  }

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
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

  @Autowired
  protected RedisTemplate<String, Object> redisTemplate;

  @BeforeEach
  void baseSetUp() {
    TenantContext.setTenantId(1L);
    cleanupDatabase();
    TenantContext.clear();
  }

  protected void cleanupDatabase() {
    if (jdbcTemplate == null)
      return;

    String[] tables = {
        "order_items", "orders", "customers", "suppliers", "products",
        "categories", "users", "tenants", "roles", "permissions",
        "notifications", "alerts", "webhooks", "audit_logs", "subscriptions",
        "subscription_plans", "support_tickets", "support_messages", "support_attachments",
        "role_permissions", "stock_movements", "invoices", "payments"
    };

    for (String table : tables) {
      try {
        jdbcTemplate.execute("TRUNCATE TABLE " + table + " RESTART IDENTITY CASCADE");
      } catch (Exception e) {
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
    if (jdbcTemplate == null)
      return;
    try {
      jdbcTemplate.execute(
          "INSERT INTO subscription_plans (name, price, max_users, max_products, billing_cycle, created_at, updated_at, version) VALUES ('FREE', 0.00, 3, 100, 'MONTHLY', NOW(), NOW(), 0)");
    } catch (Exception e) {
    }
    try {
      jdbcTemplate.execute(
          "INSERT INTO tenants (name, workspace_slug, company_code, business_type, status, is_active, created_at, updated_at, version) VALUES ('Tenant 1', 't1', 'T1001', 'RETAIL', 'ACTIVE', true, NOW(), NOW(), 0)");
      jdbcTemplate.execute(
          "INSERT INTO tenants (name, workspace_slug, company_code, business_type, status, is_active, created_at, updated_at, version) VALUES ('Tenant 2', 't2', 'T2001', 'RETAIL', 'ACTIVE', true, NOW(), NOW(), 0)");

      testTenant1Id = jdbcTemplate.queryForObject("SELECT id FROM tenants WHERE workspace_slug = 't1'", Long.class);
      testTenant2Id = jdbcTemplate.queryForObject("SELECT id FROM tenants WHERE workspace_slug = 't2'", Long.class);
    } catch (Exception e) {
    }
    try {
      jdbcTemplate
          .execute("INSERT INTO roles (name) VALUES ('ADMIN'), ('MANAGER'), ('STAFF'), ('ROOT'), ('PLATFORM_ADMIN')");
    } catch (Exception e) {
    }
    try {
      String passwordHash = passwordEncoder.encode("root123");
      jdbcTemplate.execute(
          "INSERT INTO users (name, email, password_hash, role, scope, is_active, is_verified, is_platform_user, tenant_id, created_at, updated_at, version) VALUES ('Root Admin', 'root@test.com', '"
              + passwordHash + "', 'ROOT', 'PLATFORM', true, true, true, " + testTenant1Id + ", NOW(), NOW(), 0)");
    } catch (Exception e) {
    }
    try {
      jdbcTemplate.execute(
          "INSERT INTO permissions (\"key\", description) VALUES ('read:products', 'Read Products'), ('write:products', 'Write Products'), ('read:orders', 'Read Orders'), ('write:orders', 'Write Orders')");
    } catch (Exception e) {
    }
  }
}
