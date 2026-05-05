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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.ims.config.TestRedisConfig;
import org.springframework.context.annotation.Import;

@SpringBootTest(classes = com.ims.ImsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(TestRedisConfig.class)
public abstract class BaseIntegrationTest {

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5433/ims_db");
    registry.add("spring.datasource.username", () -> "ims_user");
    registry.add("spring.datasource.password", () -> "changeme");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
  }

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
    try {
      if (mocks != null) {
        mocks.close();
      }
    } catch (Exception e) {
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
  @Mock
  protected ZSetOperations<String, Object> zSetOperations;

  private AutoCloseable mocks;

  @BeforeEach
  void baseSetUp() {
    clearTestData();
  }

  protected void cleanupDatabase() {
    clearTestData();
  }

  protected void mockRedisAndCache() {
    mocks = MockitoAnnotations.openMocks(this);
    if (redisTemplate != null) {
      redisTemplate.delete((String) any());
      when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }
  }

  protected void verifyUser(String email) throws Exception {
    if (jdbcTemplate != null) {
      jdbcTemplate.execute("UPDATE users SET is_verified = true WHERE email = '" + email + "'");
    }
  }

  protected void clearTestData() {
    if (jdbcTemplate == null)
      return;

    try {
      jdbcTemplate.execute("TRUNCATE TABLE order_items CASCADE");
    } catch (Exception e) {
    }
    try {
      jdbcTemplate.execute("TRUNCATE TABLE orders CASCADE");
    } catch (Exception e) {
    }
    try {
      jdbcTemplate.execute("TRUNCATE TABLE customers CASCADE");
    } catch (Exception e) {
    }
    try {
      jdbcTemplate.execute("TRUNCATE TABLE suppliers CASCADE");
    } catch (Exception e) {
    }
    try {
      jdbcTemplate.execute("TRUNCATE TABLE products CASCADE");
    } catch (Exception e) {
    }
    try {
      jdbcTemplate.execute("TRUNCATE TABLE categories CASCADE");
    } catch (Exception e) {
    }
    try {
      jdbcTemplate.execute("TRUNCATE TABLE users CASCADE");
    } catch (Exception e) {
    }
    try {
      jdbcTemplate.execute("TRUNCATE TABLE tenants CASCADE");
    } catch (Exception e) {
    }
    try {
      jdbcTemplate.execute("TRUNCATE TABLE roles CASCADE");
    } catch (Exception e) {
    }
    try {
      jdbcTemplate.execute("TRUNCATE TABLE permissions CASCADE");
    } catch (Exception e) {
    }
    try {
      jdbcTemplate.execute("TRUNCATE TABLE role_permissions CASCADE");
    } catch (Exception e) {
    }
    try {
      jdbcTemplate.execute("TRUNCATE TABLE stock_movements CASCADE");
    } catch (Exception e) {
    }
    try {
      jdbcTemplate.execute("TRUNCATE TABLE invoices CASCADE");
    } catch (Exception e) {
    }
    try {
      jdbcTemplate.execute("TRUNCATE TABLE payments CASCADE");
    } catch (Exception e) {
    }
    try {
      jdbcTemplate.execute("TRUNCATE TABLE subscriptions CASCADE");
    } catch (Exception e) {
    }
    try {
      jdbcTemplate.execute("TRUNCATE TABLE subscription_plans RESTART IDENTITY CASCADE");
    } catch (Exception e) {
    }
    try {
      jdbcTemplate.execute("TRUNCATE TABLE audit_logs CASCADE");
    } catch (Exception e) {
    }

    seedTestData();
  }

  protected void seedTestData() {
    if (jdbcTemplate == null)
      return;
    try {
      jdbcTemplate.execute(
          "INSERT INTO subscription_plans (name, price, max_users, max_products, billing_cycle) VALUES ('FREE', 0.00, 3, 100, 'MONTHLY')");
    } catch (Exception e) {
    }
    try {
      jdbcTemplate.execute("INSERT INTO roles (name) VALUES ('ADMIN'), ('MANAGER'), ('STAFF')");
    } catch (Exception e) {
    }
    try {
      jdbcTemplate.execute(
          "INSERT INTO permissions (\"key\", description) VALUES ('read:products', 'Read Products'), ('write:products', 'Write Products'), ('read:orders', 'Read Orders'), ('write:orders', 'Write Orders')");
    } catch (Exception e) {
    }
  }
}