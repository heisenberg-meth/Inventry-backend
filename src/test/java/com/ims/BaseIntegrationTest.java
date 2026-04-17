package com.ims;

import com.ims.tenant.repository.*;
import com.ims.platform.repository.*;
import com.ims.shared.auth.AuthService;
import com.ims.shared.auth.TenantContext;
import com.ims.product.ProductRepository;
import com.ims.category.CategoryRepository;
import com.ims.shared.audit.AuditLogRepository;
import com.ims.shared.auth.EmailVerificationRepository;
import java.util.Objects;
import java.util.Collections;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.anyString;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SuppressWarnings("null")
public abstract class BaseIntegrationTest {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
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
  protected JdbcTemplate jdbcTemplate;
  @Autowired
  protected PasswordEncoder passwordEncoder;
  @PersistenceContext
  protected EntityManager entityManager;
  @Autowired
  protected PlatformTransactionManager transactionManager;
  @Autowired
  protected EmailVerificationRepository emailVerificationRepository;
  @Autowired
  protected AuthService authService;

  @MockitoBean
  protected RedisTemplate<String, Object> redisTemplate;
  @MockitoBean
  protected ValueOperations<String, Object> valueOperations;
  @MockitoBean
  protected ZSetOperations<String, Object> zSetOperations;
  @MockitoBean
  protected org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;
  @MockitoBean
  protected org.springframework.cache.CacheManager cacheManager;
  @MockitoBean(name = "tenantAwareCacheResolver")
  protected org.springframework.cache.interceptor.CacheResolver tenantAwareCacheResolver;

  protected long systemTenantId;
  protected long testTenant1Id;
  protected long testTenant2Id;

  @BeforeEach
  void setupTenant() {
    TenantContext.setTenantId(1L);
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @BeforeEach
  void setUp() {
    mockRedisAndCache();
  }

  protected void cleanupDatabase() {
    new TransactionTemplate(Objects.requireNonNull(transactionManager)).execute(status -> {
      // Ensure tenant context is set during cleanup to avoid Hibernate issues
      TenantContext.setTenantId(1L);
      entityManager.flush();

      // PostgreSQL: Disable triggers to allow truncation of tables with FKs
      jdbcTemplate.execute("SET session_replication_role = 'replica'");

      jdbcTemplate.execute("TRUNCATE TABLE audit_logs RESTART IDENTITY CASCADE");
      jdbcTemplate.execute("TRUNCATE TABLE payments RESTART IDENTITY CASCADE");
      jdbcTemplate.execute("TRUNCATE TABLE invoices RESTART IDENTITY CASCADE");
      jdbcTemplate.execute("TRUNCATE TABLE order_items RESTART IDENTITY CASCADE");
      jdbcTemplate.execute("TRUNCATE TABLE orders RESTART IDENTITY CASCADE");
      jdbcTemplate.execute("TRUNCATE TABLE transfer_orders RESTART IDENTITY CASCADE");
      jdbcTemplate.execute("TRUNCATE TABLE stock_movements RESTART IDENTITY CASCADE");
      jdbcTemplate.execute("TRUNCATE TABLE pharmacy_products RESTART IDENTITY CASCADE");
      jdbcTemplate.execute("TRUNCATE TABLE products RESTART IDENTITY CASCADE");
      jdbcTemplate.execute("TRUNCATE TABLE categories RESTART IDENTITY CASCADE");
      jdbcTemplate.execute("TRUNCATE TABLE user_permissions RESTART IDENTITY CASCADE");
      jdbcTemplate.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
      jdbcTemplate.execute("TRUNCATE TABLE role_permissions RESTART IDENTITY CASCADE");
      jdbcTemplate.execute("TRUNCATE TABLE roles RESTART IDENTITY CASCADE");
      jdbcTemplate.execute("TRUNCATE TABLE customers RESTART IDENTITY CASCADE");
      jdbcTemplate.execute("TRUNCATE TABLE suppliers RESTART IDENTITY CASCADE");
      jdbcTemplate.execute("TRUNCATE TABLE support_attachments RESTART IDENTITY CASCADE");
      jdbcTemplate.execute("TRUNCATE TABLE support_messages RESTART IDENTITY CASCADE");
      jdbcTemplate.execute("TRUNCATE TABLE support_tickets RESTART IDENTITY CASCADE");
      jdbcTemplate.execute("TRUNCATE TABLE subscriptions RESTART IDENTITY CASCADE");
      jdbcTemplate.execute("TRUNCATE TABLE subscription_plans RESTART IDENTITY CASCADE");
      jdbcTemplate.execute("TRUNCATE TABLE tenants RESTART IDENTITY CASCADE");

      jdbcTemplate.execute("SET session_replication_role = 'origin'");

      // Seed System Tenant
      jdbcTemplate.execute(
          "INSERT INTO tenants (name, workspace_slug, business_type, status, plan, company_code) VALUES ('System', 'system', 'SYSTEM', 'ACTIVE', 'PLATFORM', 'SYS001')");
      systemTenantId = Objects.requireNonNull(
          jdbcTemplate.queryForObject("SELECT id FROM tenants WHERE workspace_slug = 'system'", Long.class));

      // Seed Root User (Linked to System Tenant)
      String rootPassHash = passwordEncoder.encode("root123");
      jdbcTemplate.update(
          "INSERT INTO users (name, email, password_hash, role, scope, tenant_id, is_active) VALUES (?, ?, ?, ?, ?, ?, ?)",
          "Root Admin", "root@ims.com", rootPassHash, "ROOT", "PLATFORM", systemTenantId, true);

      // Seed common test tenants for legacy tests
      jdbcTemplate.execute(
          "INSERT INTO tenants (name, workspace_slug, business_type, status, plan, company_code) VALUES ('Test Tenant 1', 't1', 'RETAIL', 'ACTIVE', 'FREE', 'T1001')");
      testTenant1Id = Objects.requireNonNull(
          jdbcTemplate.queryForObject("SELECT id FROM tenants WHERE workspace_slug = 't1'", Long.class));

      jdbcTemplate.execute(
          "INSERT INTO tenants (name, workspace_slug, business_type, status, plan, company_code) VALUES ('Test Tenant 2', 't2', 'RETAIL', 'ACTIVE', 'FREE', 'T2001')");
      testTenant2Id = Objects.requireNonNull(
          jdbcTemplate.queryForObject("SELECT id FROM tenants WHERE workspace_slug = 't2'", Long.class));

      entityManager.clear();
      return null;
    });
  }

  protected void verifyUserEmail(String email) {
    userRepository.findByEmailUnfiltered(email).ifPresent(u -> {
      emailVerificationRepository.findAll().stream()
          .filter(v -> v.getUserId().equals(u.getId()))
          .findFirst()
          .ifPresent(v -> authService.verifyEmail(v.getToken()));
    });
  }

  protected void mockRedisAndCache() {
    // Redis Mocks to prevent NPEs (e.g., in RateLimitFilter)
    doReturn(valueOperations).when(redisTemplate).opsForValue();
    doReturn(zSetOperations).when(redisTemplate).opsForZSet();
    doReturn(1L).when(valueOperations).increment(anyString());
    doReturn(0L).when(zSetOperations).zCard(anyString());

    // Cache Mocks for TenantAwareCacheResolver
    org.springframework.cache.Cache dummyCache = new org.springframework.cache.concurrent.ConcurrentMapCache("dummy");
    doReturn(Collections.singletonList(dummyCache))
        .when(tenantAwareCacheResolver)
        .resolveCaches(org.mockito.ArgumentMatchers.<CacheOperationInvocationContext<?>>any());
    doReturn(dummyCache).when(cacheManager).getCache(anyString());
  }
}
