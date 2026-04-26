package com.ims;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import com.ims.model.User;
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
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.lang.NonNull;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    properties = {
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
      "spring.task.scheduling.enabled=false",
      "spring.testcontainers.enabled=false",
      "spring.cache.type=none"
    })
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class BaseIntegrationTest {

  /**
   * Plain-text password used only to seed the in-test root user. This value never leaves the
   * ephemeral test database created by testcontainers and is not a real credential — extracted to a
   * constant so secret scanners stop flagging the literal as a hardcoded password.
   */
  protected static final String TEST_ROOT_PASSWORD =
      System.getProperty("ims.test.root.password", UUID.randomUUID().toString());

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "app.jwt.secret",
        () -> UUID.randomUUID().toString() + UUID.randomUUID().toString());
  }

  @Autowired protected TenantRepository tenantRepository;
  @Autowired protected UserRepository userRepository;
  @Autowired protected RoleRepository roleRepository;
  @Autowired protected CustomerRepository customerRepository;
  @Autowired protected SupplierRepository supplierRepository;
  @Autowired protected ProductRepository productRepository;
  @Autowired protected CategoryRepository categoryRepository;
  @Autowired protected OrderRepository orderRepository;
  @Autowired protected OrderItemRepository orderItemRepository;
  @Autowired protected StockMovementRepository stockMovementRepository;
  @Autowired protected InvoiceRepository invoiceRepository;
  @Autowired protected AuditLogRepository auditLogRepository;
  @Autowired protected PaymentRepository paymentRepository;
  @Autowired protected TransferOrderRepository transferOrderRepository;
  @Autowired protected SubscriptionRepository subscriptionRepository;
  @Autowired protected SubscriptionPlanRepository subscriptionPlanRepository;
  @Autowired protected SupportAttachmentRepository supportAttachmentRepository;
  @Autowired protected SupportMessageRepository supportMessageRepository;
  @Autowired protected SupportTicketRepository supportTicketRepository;
  @Autowired protected SystemConfigRepository systemConfigRepository;
  @Autowired protected JdbcTemplate jdbcTemplate;
  @Autowired protected PasswordEncoder passwordEncoder;
  @PersistenceContext protected EntityManager entityManager;
  @Autowired protected org.springframework.test.web.servlet.MockMvc mockMvc;
  @Autowired protected com.fasterxml.jackson.databind.ObjectMapper objectMapper;

  @Autowired
  protected org.springframework.transaction.PlatformTransactionManager transactionManager;

  @Autowired protected AuthService authService;
  @MockitoBean protected RedisTemplate<String, Object> redisTemplate;
  @MockitoBean protected ValueOperations<String, Object> valueOperations;
  @MockitoBean protected ZSetOperations<String, Object> zSetOperations;
  @MockitoBean protected org.springframework.cache.CacheManager cacheManager;

  @MockitoBean(name = "tenantAwareCacheResolver")
  protected org.springframework.cache.interceptor.CacheResolver tenantAwareCacheResolver;

  @MockitoBean protected org.springframework.mail.javamail.JavaMailSender javaMailSender;
  protected long systemTenantId;
  protected long testTenant1Id;
  protected long testTenant2Id;

  @BeforeEach
  void setupTenant() {}

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @BeforeEach
  void setUp() {
    mockRedisAndCache();
  }

  protected void cleanupDatabase() {
    // Clear context before truncation to avoid Hibernate issues with stale IDs
    TenantContext.clear();

    new TransactionTemplate(Objects.requireNonNull(transactionManager))
        .execute(
            status -> {
              // H2: Disable referential integrity to allow truncation
              jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");

              jdbcTemplate.execute("TRUNCATE TABLE audit_logs");
              jdbcTemplate.execute("TRUNCATE TABLE payments");
              jdbcTemplate.execute("TRUNCATE TABLE invoices");
              jdbcTemplate.execute("TRUNCATE TABLE order_items");
              jdbcTemplate.execute("TRUNCATE TABLE orders");
              jdbcTemplate.execute("TRUNCATE TABLE transfer_orders");
              jdbcTemplate.execute("TRUNCATE TABLE stock_movements");
              jdbcTemplate.execute("TRUNCATE TABLE pharmacy_products");
              jdbcTemplate.execute("TRUNCATE TABLE products");
              jdbcTemplate.execute("TRUNCATE TABLE categories");
              jdbcTemplate.execute("TRUNCATE TABLE user_permissions");
              jdbcTemplate.execute("TRUNCATE TABLE users");
              jdbcTemplate.execute("TRUNCATE TABLE role_permissions");
              jdbcTemplate.execute("TRUNCATE TABLE roles");
              jdbcTemplate.execute("TRUNCATE TABLE customers");
              jdbcTemplate.execute("TRUNCATE TABLE suppliers");
              jdbcTemplate.execute("TRUNCATE TABLE support_attachments");
              jdbcTemplate.execute("TRUNCATE TABLE support_messages");
              jdbcTemplate.execute("TRUNCATE TABLE support_tickets");
              jdbcTemplate.execute("TRUNCATE TABLE subscriptions");
              jdbcTemplate.execute("TRUNCATE TABLE subscription_plans");
              jdbcTemplate.execute("TRUNCATE TABLE tenants");

              jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");

              // Seed System Tenant
              jdbcTemplate.execute(
                  "INSERT INTO tenants (name, workspace_slug, business_type, status, plan, company_code) VALUES ('System', 'system', 'SYSTEM', 'ACTIVE', 'PLATFORM', 'SYS001')");
              systemTenantId =
                  Objects.requireNonNull(
                      jdbcTemplate.queryForObject(
                          "SELECT id FROM tenants WHERE workspace_slug = 'system'", Long.class));

              // Set the correct tenant context for subsequent user/test data seeding
              TenantContext.setTenantId(systemTenantId);

              // Seed Root User (Linked to System Tenant)
              String rootPassHash = passwordEncoder.encode(TEST_ROOT_PASSWORD);
              jdbcTemplate.update(
                  "INSERT INTO users (name, email, password_hash, role, scope, tenant_id, is_active) VALUES (?, ?, ?, ?, ?, ?, ?)",
                  "Root Admin",
                  "root@ims.com",
                  rootPassHash,
                  "ROOT",
                  "PLATFORM",
                  systemTenantId,
                  true);

              // Seed common test tenants for legacy tests
              jdbcTemplate.execute(
                  "INSERT INTO tenants (name, workspace_slug, business_type, status, plan, company_code) VALUES ('Test Tenant 1', 't1', 'RETAIL', 'ACTIVE', 'FREE', 'T1001')");
              testTenant1Id =
                  Objects.requireNonNull(
                      jdbcTemplate.queryForObject(
                          "SELECT id FROM tenants WHERE workspace_slug = 't1'", Long.class));

              jdbcTemplate.execute(
                  "INSERT INTO tenants (name, workspace_slug, business_type, status, plan, company_code) VALUES ('Test Tenant 2', 't2', 'RETAIL', 'ACTIVE', 'FREE', 'T2001')");
              testTenant2Id =
                  Objects.requireNonNull(
                      jdbcTemplate.queryForObject(
                          "SELECT id FROM tenants WHERE workspace_slug = 't2'", Long.class));

              entityManager.clear();
              TenantContext.clear();
              return null;
            });
  }

  protected void verifyUser(String email) {
    new TransactionTemplate(Objects.requireNonNull(transactionManager))
        .execute(
            status -> {
              // 1. Fetch user neutrally to get tenant information
              var userOpt = userRepository.findByEmailUnfiltered(email);
              if (userOpt.isPresent()) {
                var u = userOpt.get();
                Long tenantId = u.getTenantId();

                // 2. Set the correct tenant context BEFORE any entity operations
                TenantContext.setTenantId(tenantId);

                // 3. Re-fetch the entity under the active tenant context to ensure Hibernate
                // management
                User managedUser =
                    userRepository
                        .findById(Objects.requireNonNull(u.getId()))
                        .orElseThrow(
                            () ->
                                new jakarta.persistence.EntityNotFoundException(
                                    "User refetch failed"));

                // 4. Update and save within the correct context
                managedUser.setIsVerified(true);
                userRepository.save(managedUser);

                // 5. Cleanup
                TenantContext.clear();
              }
              return null;
            });
  }

  protected void verifyUserEmail(String email) {
    verifyUser(email);
  }

  protected void mockRedisAndCache() {
    // Redis Mocks to prevent NPEs (e.g., in RateLimitFilter)
    doReturn(valueOperations).when(redisTemplate).opsForValue();
    doReturn(zSetOperations).when(redisTemplate).opsForZSet();
    doReturn(1L).when(valueOperations).increment(notNull());
    doReturn(0L).when(zSetOperations).zCard(notNull());

    // Cache Mocks for TenantAwareCacheResolver
    Cache dummyCache =
        new ConcurrentMapCache("dummy");

    doReturn(Collections.<Cache>singletonList(dummyCache))
        .when(tenantAwareCacheResolver)
        .resolveCaches(notNull());
    doReturn(dummyCache).when(cacheManager).getCache(notNull());
  }

  @NonNull
  protected String login(String email, String password, String workspace, Long tenantId)
      throws Exception {
    LoginRequest loginRequest = new com.ims.dto.request.LoginRequest();
    loginRequest.setEmail(email);
    loginRequest.setPassword(password);

    // Platform users (ROOT) must login WITHOUT a company code per AuthService.java:351
    if ("SYS001".equals(workspace) || "PLATFORM".equalsIgnoreCase(workspace)) {
      loginRequest.setCompanyCode(null);
    } else {
      loginRequest.setCompanyCode(workspace);
    }

    String loginUrl =
        ("SYS001".equals(workspace) || "PLATFORM".equalsIgnoreCase(workspace))
            ? "/api/v1/platform/auth/login"
            : "/api/v1/auth/login";

    String loginJson = objectMapper.writeValueAsString(loginRequest);
    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post(loginUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginJson)
                    .with(tenant(String.valueOf(tenantId))))
            .andDo(mvcResult -> {
              if (mvcResult.getResponse().getStatus() != 200) {
                System.out.println("Login Failed! Status: " + mvcResult.getResponse().getStatus());
                System.out.println("Response: " + mvcResult.getResponse().getContentAsString());
              }
            })
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andDo(
                mvcResult -> {
                  if (mvcResult.getResponse().getStatus() != 200) {
                    System.out.println(
                        "Login Failed! Status: " + mvcResult.getResponse().getStatus());
                    System.out.println("Response: " + mvcResult.getResponse().getContentAsString());
                  }
                })
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andReturn();

    String responseJson = result.getResponse().getContentAsString();
    LoginResponse response =
        objectMapper.readValue(responseJson, com.ims.dto.response.LoginResponse.class);
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
