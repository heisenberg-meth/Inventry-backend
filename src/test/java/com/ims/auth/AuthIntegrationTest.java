package com.ims.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.LoginResponse;
import com.ims.platform.repository.TenantRepository;
import com.ims.shared.auth.SignupService;
import com.ims.tenant.repository.UserRepository;
import com.ims.shared.audit.AuditLogRepository;
import com.ims.tenant.repository.RoleRepository;
import com.ims.tenant.repository.CustomerRepository;
import com.ims.tenant.repository.SupplierRepository;
import com.ims.tenant.repository.OrderRepository;
import com.ims.tenant.repository.OrderItemRepository;
import com.ims.tenant.repository.ProductRepository;
import com.ims.tenant.repository.StockMovementRepository;
import com.ims.tenant.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.Objects;
import java.util.Map;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private SignupService signupService;
  @Autowired private UserRepository userRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private AuditLogRepository auditLogRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private SupplierRepository supplierRepository;
  @Autowired private OrderRepository orderRepository;
  @Autowired private OrderItemRepository orderItemRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private StockMovementRepository stockMovementRepository;
  @Autowired private InvoiceRepository invoiceRepository;

  @MockitoBean private RedisTemplate<String, Object> redisTemplate;
  @MockitoBean private ValueOperations<String, Object> valueOperations;
  @MockitoBean private ZSetOperations<String, Object> zSetOperations;
  @MockitoBean private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;
  @MockitoBean private org.springframework.cache.CacheManager cacheManager;
  @MockitoBean private org.springframework.cache.interceptor.CacheResolver tenantAwareCacheResolver;

  @BeforeEach
  void setup() {
    auditLogRepository.deleteAll();
    invoiceRepository.deleteAll();
    orderItemRepository.deleteAll();
    orderRepository.deleteAll();
    stockMovementRepository.deleteAll();
    productRepository.deleteAll();
    userRepository.deleteAll();
    roleRepository.deleteAll();
    customerRepository.deleteAll();
    supplierRepository.deleteAll();
    tenantRepository.deleteAll();
    
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
  }

  @Test
  void testSecurityAndIsolationFlow() throws Exception {
    // 1. Signup Tenant 1
    SignupRequest t1Signup = createSignupRequest("Tenant 1", "t1", "admin1@t1.com");
    signupService.signup(t1Signup);

    // 2. Signup Tenant 2
    SignupRequest t2Signup = createSignupRequest("Tenant 2", "t2", "admin2@t2.com");
    signupService.signup(t2Signup);

    // 3. Login Tenant 1
    String t1Token = login("admin1@t1.com", "password123", "t1");

    // 4. Verify Tenant 1 Isolation (Should only see 1 user: admin1)
    mockMvc
        .perform(get("/api/tenant/users").header("Authorization", "Bearer " + t1Token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].email").value("admin1@t1.com"));

    // 5. Login Tenant 2
    String t2Token = login("admin2@t2.com", "password123", "t2");

    // 6. Verify Tenant 2 Isolation (Should only see 1 user: admin2)
    mockMvc
        .perform(get("/api/tenant/users").header("Authorization", "Bearer " + t2Token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].email").value("admin2@t2.com"));

    // 7. Verify Logout and Blacklisting
    mockMvc
        .perform(post("/api/auth/logout").header("Authorization", "Bearer " + t1Token))
        .andExpect(status().isOk());

    // Mock Redis blacklist check for next request
    when(redisTemplate.hasKey(anyString())).thenReturn(true);

    mockMvc
        .perform(get("/api/tenant/users").header("Authorization", "Bearer " + t1Token))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void testUnauthorizedAccess() throws Exception {
    mockMvc.perform(get("/api/tenant/users")).andExpect(status().isUnauthorized());
  }

  private SignupRequest createSignupRequest(String name, String workspaceSlug, String email) {
    SignupRequest req = new SignupRequest();
    req.setBusinessName(name);
    req.setBusinessType("Retail");
    req.setWorkspaceSlug(workspaceSlug);
    req.setOwnerName("Owner " + name);
    req.setOwnerEmail(email);
    req.setPassword("password123");
    return req;
  }

  private String login(String email, String password, String workspace) throws Exception {
    LoginRequest loginRequest = new LoginRequest();
    loginRequest.setEmail(email);
    loginRequest.setPassword(password);
    loginRequest.setCompanyCode(workspace);
    
    MvcResult result =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(Objects.requireNonNull(objectMapper.writeValueAsString(loginRequest))))
            .andExpect(status().isOk())
            .andReturn();

    String responseJson = result.getResponse().getContentAsString();
    LoginResponse response = objectMapper.readValue(responseJson, LoginResponse.class);
    return response.getAccessToken();
  }
}
