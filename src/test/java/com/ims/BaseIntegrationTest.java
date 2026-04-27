package com.ims;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import com.ims.model.User;
import com.ims.model.Role;
import com.ims.category.CategoryRepository;
import com.ims.platform.repository.*;
import com.ims.product.ProductRepository;
import com.ims.shared.audit.AuditLogRepository;
import com.ims.shared.auth.AuthService;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.repository.*;
import jakarta.persistence.EntityManager;
import com.ims.dto.response.LoginResponse;
import com.ims.dto.request.LoginRequest;
import jakarta.persistence.PersistenceContext;
import java.util.Objects;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.http.MediaType;
import org.springframework.cache.Cache;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.lang.NonNull;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
    "spring.task.scheduling.enabled=false",
    "spring.testcontainers.enabled=true",
    "spring.cache.type=none"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class BaseIntegrationTest {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    System.setProperty("app.test.mode", "true");
  }

  protected static final String TEST_ROOT_PASSWORD = System.getProperty("ims.test.root.password",
      UUID.randomUUID().toString());

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "app.jwt.secret",
        () -> UUID.randomUUID().toString() + UUID.randomUUID().toString());
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

  @jakarta.annotation.PostConstruct
  public void debugConfig() {
    try {
      Object mt = entityManager.getEntityManagerFactory()
          .getProperties()
          .get("hibernate.multiTenancy");
      Object cache = entityManager.getEntityManagerFactory()
          .getProperties()
          .get("hibernate.cache.region.factory_class");
      System.out.println("DEBUG - Hibernate MultiTenancy Mode: " + mt);
      System.out.println("DEBUG - Hibernate Cache Factory: " + cache);
    } catch (Exception e) {
      System.out.println("DEBUG - Failed to get properties: " + e.getMessage());
    }
  }

  @Autowired
  protected org.springframework.test.web.servlet.MockMvc mockMvc;
  @Autowired
  protected com.fasterxml.jackson.databind.ObjectMapper objectMapper;

  @Autowired
  protected org.springframework.transaction.PlatformTransactionManager transactionManager;

  @Autowired
  protected AuthService authService;
  @MockitoBean
  protected RedisTemplate<String, Object> redisTemplate;
  @MockitoBean
  protected ValueOperations<String, Object> valueOperations;
  @MockitoBean
  protected ZSetOperations<String, Object> zSetOperations;
  @MockitoBean
  protected org.springframework.cache.CacheManager cacheManager;

  @MockitoBean(name = "tenantAwareCacheResolver")
  protected org.springframework.cache.interceptor.CacheResolver tenantAwareCacheResolver;

  @MockitoBean
  protected org.springframework.mail.javamail.JavaMailSender javaMailSender;
  protected long systemTenantId;
  protected long testTenant1Id;
  protected long testTenant2Id;

  @BeforeEach
  void setupTenant() {
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @BeforeEach
  void setUp() {
    if (TEST_ROOT_PASSWORD == null || TEST_ROOT_PASSWORD.isBlank()) {
      throw new IllegalStateException("TEST_ROOT_PASSWORD must be configured for integration tests.");
    }
    mockRedisAndCache();
  }

  protected void cleanupDatabase() {
    TenantContext.clear();

    new TransactionTemplate(Objects.requireNonNull(transactionManager))
        .execute(
            status -> {
              jdbcTemplate.execute("TRUNCATE TABLE audit_logs, payments, invoices, order_items, orders, " +
                  "transfer_orders, stock_movements, pharmacy_products, products, categories, " +
                  "user_permissions, users, role_permissions, roles, customers, suppliers, " +
                  "support_attachments, support_messages, support_tickets, subscriptions, " +
                  "subscription_plans, tenants RESTART IDENTITY CASCADE");

              // Seed System Tenant
              jdbcTemplate.execute(
                  "INSERT INTO tenants (name, workspace_slug, business_type, status, plan, company_code) VALUES ('System', 'system', 'SYSTEM', 'ACTIVE', 'PLATFORM', 'SYS001')");
              systemTenantId = Objects.requireNonNull(
                  jdbcTemplate.queryForObject(
                      "SELECT id FROM tenants WHERE workspace_slug = 'system'", Long.class));

              TenantContext.setTenantId(systemTenantId);

              // Seed ROOT Role
              jdbcTemplate.update(
                  "INSERT INTO roles (name, description, tenant_id) VALUES (?, ?, ?)",
                  "ROOT",
                  "Platform Root Administrator",
                  null);
              long rootRoleId = Objects.requireNonNull(
                  jdbcTemplate.queryForObject("SELECT id FROM roles WHERE name = 'ROOT' AND tenant_id IS NULL",
                      Long.class));

              // Seed Root User
              String rootPassHash = passwordEncoder.encode(Objects.requireNonNull(TEST_ROOT_PASSWORD));
              jdbcTemplate.update(
                  "INSERT INTO users (name, email, password_hash, role_id, scope, tenant_id, is_active) VALUES (?, ?, ?, ?, ?, ?, ?)",
                  "Root Admin",
                  "root@ims.com",
                  rootPassHash,
                  rootRoleId,
                  "PLATFORM",
                  systemTenantId,
                  true);

              // Seed common test tenants
              jdbcTemplate.execute(
                  "INSERT INTO tenants (name, workspace_slug, business_type, status, plan, company_code) VALUES ('Test Tenant 1', 't1', 'RETAIL', 'ACTIVE', 'FREE', 'T1001')");
              testTenant1Id = Objects.requireNonNull(
                  jdbcTemplate.queryForObject(
                      "SELECT id FROM tenants WHERE workspace_slug = 't1'", Long.class));

              jdbcTemplate.execute(
                  "INSERT INTO tenants (name, workspace_slug, business_type, status, plan, company_code) VALUES ('Test Tenant 2', 't2', 'RETAIL', 'ACTIVE', 'FREE', 'T2001')");
              testTenant2Id = Objects.requireNonNull(
                  jdbcTemplate.queryForObject(
                      "SELECT id FROM tenants WHERE workspace_slug = 't2'", Long.class));

              entityManager.clear();
              TenantContext.clear();
              return null;
            });
  }

  /**
   * Helper to get or create a role for a tenant.
   */
  protected Role getOrCreateRole(@NonNull String name, @NonNull Long tenantId) {
    Long previous = TenantContext.getTenantId();
    TenantContext.setTenantId(tenantId);
    try {
      return roleRepository.findByName(name)
          .orElseGet(() -> roleRepository.save(Role.builder().name(name).tenantId(tenantId).build()));
    } finally {
      TenantContext.setTenantId(previous);
    }
  }

  protected void verifyUser(String email) {
    new TransactionTemplate(Objects.requireNonNull(transactionManager))
        .execute(
            status -> {
              var userOpt = userRepository.findByEmailGlobal(email);
              if (userOpt.isPresent()) {
                var u = userOpt.get();
                Long tenantId = u.getTenantId();
                TenantContext.setTenantId(tenantId);
                User managedUser = userRepository
                    .findById(Objects.requireNonNull(u.getId()))
                    .orElseThrow(
                        () -> new jakarta.persistence.EntityNotFoundException(
                            "User refetch failed"));
                managedUser.setIsVerified(true);
                userRepository.save(managedUser);
                TenantContext.clear();
              }
              return null;
            });
  }

  protected void verifyUserEmail(String email) {
    verifyUser(email);
  }

  protected void mockRedisAndCache() {
    doReturn(valueOperations).when(redisTemplate).opsForValue();
    doReturn(zSetOperations).when(redisTemplate).opsForZSet();
    doReturn(1L).when(valueOperations).increment(Objects.requireNonNull(notNull()));
    doReturn(0L).when(zSetOperations).zCard(Objects.requireNonNull(notNull()));

    Cache dummyCache = new ConcurrentMapCache("dummy");

    doReturn(Collections.<Cache>singletonList(dummyCache))
        .when(tenantAwareCacheResolver)
        .resolveCaches(Objects.requireNonNull(notNull()));
    doReturn(dummyCache).when(cacheManager).getCache(Objects.requireNonNull(notNull()));
  }

  @NonNull
  protected String login(@NonNull String email, @NonNull String password, @NonNull String workspace,
      @NonNull Long tenantId)
      throws Exception {
    LoginRequest loginRequest = new com.ims.dto.request.LoginRequest();
    loginRequest.setEmail(email);
    loginRequest.setPassword(password);

    if ("SYS001".equals(workspace) || "PLATFORM".equalsIgnoreCase(workspace)) {
      loginRequest.setCompanyCode(null);
    } else {
      loginRequest.setCompanyCode(workspace);
    }

    String loginUrl = ("SYS001".equals(workspace) || "PLATFORM".equalsIgnoreCase(workspace))
        ? "/api/v1/platform/auth/login"
        : "/api/v1/auth/login";

    String loginJson = objectMapper.writeValueAsString(loginRequest);
    MvcResult result = mockMvc
        .perform(
            MockMvcRequestBuilders.post(loginUrl)
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(loginJson))
                .with(tenant(Objects.requireNonNull(String.valueOf(tenantId)))))
        .andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    String responseJson = result.getResponse().getContentAsString();
    LoginResponse response = objectMapper.readValue(responseJson, com.ims.dto.response.LoginResponse.class);
    return Objects.requireNonNull(response.getAccessToken());
  }

  @NonNull
  protected RequestPostProcessor tenant(
      @NonNull Object tenantId) {
    return request -> {
      request.addHeader("X-Tenant-ID", Objects.requireNonNull(String.valueOf(tenantId)));
      return request;
    };
  }
}
