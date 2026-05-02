package com.ims;

import org.mockito.Mockito;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import com.ims.model.User;
import com.ims.model.Role;
import com.ims.shared.auth.AuthService;
import com.ims.shared.auth.JwtAuthDetails;
import com.ims.dto.request.LoginRequest;
import com.ims.shared.auth.TenantContext;
import com.ims.config.TestSecurityConfig;
import com.ims.product.ProductRepository;
import com.ims.dto.response.LoginResponse;
import com.ims.category.CategoryRepository;
import com.ims.shared.audit.AuditLogRepository;
import com.ims.tenant.repository.UserRepository;
import com.ims.tenant.repository.RoleRepository;
import com.ims.tenant.repository.OrderRepository;
import com.ims.tenant.repository.InvoiceRepository;
import com.ims.tenant.repository.PaymentRepository;
import com.ims.tenant.repository.SupplierRepository;
import com.ims.tenant.repository.CustomerRepository;
import com.ims.platform.repository.TenantRepository;
import com.ims.tenant.repository.OrderItemRepository;
import com.ims.tenant.repository.TransferOrderRepository;
import com.ims.tenant.repository.SupportTicketRepository;
import com.ims.tenant.repository.StockMovementRepository;
import com.ims.platform.repository.SubscriptionRepository;
import com.ims.tenant.repository.SupportMessageRepository;
import com.ims.platform.repository.SystemConfigRepository;
import com.ims.tenant.repository.SupportAttachmentRepository;
import com.ims.platform.repository.SubscriptionPlanRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import java.util.Objects;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.http.MediaType;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration",

    "spring.task.scheduling.enabled=false",
    "spring.testcontainers.enabled=true",
    "spring.cache.type=none",

    // Flyway manages schema
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.properties.hibernate.cache.use_second_level_cache=false",
    "spring.jpa.properties.hibernate.cache.use_query_cache=false",
    "spring.jpa.properties.hibernate.cache.region.factory_class=org.hibernate.cache.internal.NoCachingRegionFactory"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(TestSecurityConfig.class)
public abstract class BaseIntegrationTest {
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    postgres.start();
    System.setProperty("DOCKER_API_VERSION", "1.41");
  }

  protected static final String TEST_ROOT_PASSWORD = System.getProperty("ims.test.root.password",
      "TestPass123!");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
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
  @MockitoSpyBean
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
  protected MockMvc mockMvc;
  @Autowired
  protected ObjectMapper objectMapper;

  @Autowired
  protected PlatformTransactionManager transactionManager;

  @Autowired
  protected AuthService authService;
  @MockitoBean
  protected RedisTemplate<String, Object> redisTemplate;
  @MockitoBean
  protected ValueOperations<String, Object> valueOperations;
  @MockitoBean
  protected ZSetOperations<String, Object> zSetOperations;
  @MockitoBean
  protected CacheManager cacheManager;

  @MockitoBean(name = "tenantAwareCacheResolver")
  protected CacheResolver tenantAwareCacheResolver;

  @MockitoBean
  protected JavaMailSender javaMailSender;
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
  protected void setUp() throws Exception {
    if (TEST_ROOT_PASSWORD == null || TEST_ROOT_PASSWORD.isBlank()) {
      throw new IllegalStateException("TEST_ROOT_PASSWORD must be configured for integration tests.");
    }
    mockRedisAndCache();
    Mockito.lenient().when(passwordEncoder.encode(anyString()))
        .thenReturn("$2a$10$dummyhashdummyhash");
    Mockito.lenient().when(passwordEncoder.matches(anyString(), anyString()))
        .thenReturn(true);
  }

  protected void setupRootAuthentication() {
    if (systemTenantId == 0) {
      return; // Not initialized yet
    }
    // Set up ROOT with all common roles for testing
    List<SimpleGrantedAuthority> authorities = Arrays.asList(
        new SimpleGrantedAuthority("ROLE_ROOT"),
        new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"),
        new SimpleGrantedAuthority("ROLE_BUSINESS_MANAGER"),
        new SimpleGrantedAuthority("ROLE_SALES_STAFF"));

    JwtAuthDetails details = new JwtAuthDetails(
        1L, systemTenantId, "ROOT", "PLATFORM", "SYSTEM", false,
        Collections.emptySet(), false, null);

    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
        1L, details, authorities);
    SecurityContextHolder.getContext().setAuthentication(auth);
    TenantContext.setTenantId(systemTenantId);
  }

  protected void cleanupDatabase() {
    TenantContext.clear();

    new TransactionTemplate(Objects.requireNonNull(transactionManager))
        .execute(
            status -> {
              // We use TRUNCATE ONLY for tables that might be partitioned
              jdbcTemplate.execute("TRUNCATE TABLE audit_logs, payments, invoices, order_items, orders, " +
                  "transfer_orders, stock_movements, pharmacy_products, products, categories, " +
                  "user_permissions, users, role_permissions, roles, customers, suppliers, " +
                  "support_attachments, support_messages, support_tickets, subscriptions, " +
                  "subscription_plans, tenants RESTART IDENTITY CASCADE");

              jdbcTemplate.execute("ALTER TABLE roles ALTER COLUMN tenant_id DROP NOT NULL");
              jdbcTemplate.execute("ALTER TABLE users ALTER COLUMN tenant_id DROP NOT NULL");

              // Seed System Tenant
              jdbcTemplate.execute(
                  "INSERT INTO tenants (name, workspace_slug, business_type, status, plan, company_code, version) " +
                      "VALUES ('System', 'system', 'SYSTEM', 'ACTIVE', 'PLATFORM', 'SYS001', 0)");
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

              // Seed Root User - Using systemTenantId to satisfy NOT NULL constraint
              String rootPassHash = passwordEncoder.encode(Objects.requireNonNull(TEST_ROOT_PASSWORD));

              MapSqlParameterSource params = new MapSqlParameterSource();
              params.addValue("name", "Root Admin");
              params.addValue("email", "root@ims.com");
              params.addValue("password_hash", rootPassHash);
              params.addValue("role_id", rootRoleId);
              params.addValue("scope", "PLATFORM");
              params.addValue("tenant_id", systemTenantId); // Root user linked to System tenant
              params.addValue("is_active", true);
              params.addValue("is_platform_user", true);
              params.addValue("is_verified", true);
              params.addValue("two_factor_enabled", false);
              params.addValue("version", 0);

              new NamedParameterJdbcTemplate(jdbcTemplate)
                  .update(
                      "INSERT INTO users (name, email, password_hash, role_id, scope, tenant_id, is_active, is_platform_user, is_verified, two_factor_enabled, version) "
                          +
                          "VALUES (:name, :email, :password_hash, :role_id, :scope, :tenant_id, :is_active, :is_platform_user, :is_verified, :two_factor_enabled, :version)",
                      params);

              // Seed common test tenants
              jdbcTemplate.execute(
                  "INSERT INTO tenants (name, workspace_slug, business_type, status, plan, company_code, version) VALUES ('Test Tenant 1', 't1', 'RETAIL', 'ACTIVE', 'FREE', 'T1001', 0)");
              testTenant1Id = Objects.requireNonNull(
                  jdbcTemplate.queryForObject(
                      "SELECT id FROM tenants WHERE workspace_slug = 't1'", Long.class));

              jdbcTemplate.execute(
                  "INSERT INTO tenants (name, workspace_slug, business_type, status, plan, company_code, version) VALUES ('Test Tenant 2', 't2', 'RETAIL', 'ACTIVE', 'FREE', 'T2001', 0)");
              testTenant2Id = Objects.requireNonNull(
                  jdbcTemplate.queryForObject(
                      "SELECT id FROM tenants WHERE workspace_slug = 't2'", Long.class));

              // Seed DEFAULT Subscription Plan
              jdbcTemplate.execute(
                  "INSERT INTO subscription_plans (name, status, is_default, billing_cycle, version) " +
                      "VALUES ('DEFAULT', 'ACTIVE', true, 'MONTHLY', 0)");

              // FR-03-J: Seed ROOT permissions so ROOT user actually has authorities in tests
              jdbcTemplate.execute(
                  "INSERT INTO role_permissions (role_id, permission_id) " +
                      "SELECT r.id, p.id FROM roles r, permissions p " +
                      "WHERE r.name = 'ROOT' AND r.tenant_id IS NULL");

              // Enable Support Mode for tests to allow RBAC overrides
              jdbcTemplate.execute(
                  "INSERT INTO system_configs (config_key, config_value, description) " +
                      "VALUES ('SUPPORT_MODE', 'true', 'Enable root override for tests')");

              entityManager.clear();
              TenantContext.clear();
              setupRootAuthentication();
              return null;
            });
  }

  /**
   * Helper to get or create a role for a tenant.
   */
  protected Role getOrCreateRole(String name, Long tenantId) {
    Long previous = TenantContext.getTenantId();
    TenantContext.setTenantId(tenantId);
    try {
      return roleRepository.findByName(name)
          .orElseGet(() -> roleRepository.save(Role.builder().name(name).build()));
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

    doReturn(1L).when(valueOperations).increment(anyString());
    doReturn(0L).when(zSetOperations).zCard(anyString());

    Cache dummyCache = new ConcurrentMapCache("dummy");

    doReturn(Collections.singletonList(dummyCache))
        .when(tenantAwareCacheResolver)
        .resolveCaches(any());

    doReturn(dummyCache)
        .when(cacheManager)
        .getCache(anyString());
  }

  protected String login(String email, String password, String workspace,
      Long tenantId)
      throws Exception {
    LoginRequest loginRequest = new LoginRequest();
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
    LoginResponse response = objectMapper.readValue(responseJson, LoginResponse.class);
    return Objects.requireNonNull(response.getAccessToken());
  }

  protected RequestPostProcessor tenant(
      Object tenantId) {
    return request -> {
      request.addHeader("X-Tenant-ID", Objects.requireNonNull(String.valueOf(tenantId)));
      // Set TenantContext directly for Hibernate multi-tenancy
      Long tid = Long.parseLong(String.valueOf(tenantId));
      TenantContext.setTenantId(tid);

      // Also set SecurityContext with JwtAuthDetails for code that expects tenant
      // from security context
      JwtAuthDetails details = new JwtAuthDetails(
          1L, tid, "ROOT", "PLATFORM", "SYSTEM", false,
          Collections.emptySet(), false, null);

      UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
          1L, details, List.of(new SimpleGrantedAuthority("ROLE_ROOT")));
      auth.setDetails(details); // IMPORTANT: Set details separately!

      // Properly set SecurityContext
      SecurityContext context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(auth);
      SecurityContextHolder.setContext(context);

      return request;
      //
    };

  }
}