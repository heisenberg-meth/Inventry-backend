package com.ims;

import com.ims.platform.repository.*;
import com.ims.shared.audit.AuditLogRepository;
import com.ims.tenant.repository.*;
import com.ims.product.ProductRepository;
import com.ims.category.CategoryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import java.util.Collections;
import java.util.Objects;
import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.anyString;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;

@SuppressWarnings("null")
public abstract class BaseIntegrationTest {

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

  protected void cleanupDatabase() {
    new TransactionTemplate(Objects.requireNonNull(transactionManager)).execute(status -> {
      entityManager.flush();
      jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");

      jdbcTemplate.execute("DELETE FROM audit_logs");
      jdbcTemplate.execute("DELETE FROM payments");
      jdbcTemplate.execute("DELETE FROM invoices");
      jdbcTemplate.execute("DELETE FROM order_items");
      jdbcTemplate.execute("DELETE FROM orders");
      jdbcTemplate.execute("DELETE FROM transfer_orders");
      jdbcTemplate.execute("DELETE FROM stock_movements");
      jdbcTemplate.execute("DELETE FROM pharmacy_products");
      jdbcTemplate.execute("DELETE FROM products");
      jdbcTemplate.execute("DELETE FROM categories");
      jdbcTemplate.execute("DELETE FROM user_permissions");
      jdbcTemplate.execute("DELETE FROM users");
      jdbcTemplate.execute("DELETE FROM role_permissions");
      jdbcTemplate.execute("DELETE FROM roles");
      jdbcTemplate.execute("DELETE FROM customers");
      jdbcTemplate.execute("DELETE FROM suppliers");
      jdbcTemplate.execute("DELETE FROM support_attachments");
      jdbcTemplate.execute("DELETE FROM support_messages");
      jdbcTemplate.execute("DELETE FROM support_tickets");
      jdbcTemplate.execute("DELETE FROM subscriptions");
      jdbcTemplate.execute("DELETE FROM subscription_plans");
      jdbcTemplate.execute("DELETE FROM tenants");

      jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");

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
