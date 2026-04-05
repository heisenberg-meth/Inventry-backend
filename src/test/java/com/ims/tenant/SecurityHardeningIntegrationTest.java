package com.ims.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.dto.request.SignupRequest;
import com.ims.shared.auth.SignupService;
import com.ims.tenant.repository.UserRepository;
import com.ims.platform.repository.TenantRepository;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.util.Map;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SecurityHardeningIntegrationTest {

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
    
    when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    
    org.springframework.cache.Cache dummyCache = new org.springframework.cache.concurrent.ConcurrentMapCache("dummy");
    doReturn(java.util.Collections.singletonList(dummyCache)).when(tenantAwareCacheResolver).resolveCaches(any());
    when(cacheManager.getCache(anyString())).thenReturn(dummyCache);
  }

  @Test
  void testCorrelationIdInHeadersAndError() throws Exception {
    mockMvc.perform(get("/api/auth/invalid-path"))
        .andExpect(status().isNotFound())
        .andExpect(header().exists("X-Correlation-ID"))
        .andExpect(jsonPath("$.correlation_id").exists());
  }

  @Test
  void testRateLimitEnforcement() throws Exception {
    // Mock Redis to return 100 requests already made (Public limit is 50)
    when(zSetOperations.zCard(anyString())).thenReturn(100L);

    mockMvc.perform(get("/api/any"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("X-RateLimit-Limit", "50"))
        .andExpect(jsonPath("$.error").value("Too Many Requests"));
  }

  @Test
  void testAuthRateLimitEnforcement() throws Exception {
    // Mock Redis for auth endpoint (Limit is 20)
    when(zSetOperations.zCard(anyString())).thenReturn(25L);

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("email","a@b.com","password","p","companyCode","c"))))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("X-RateLimit-Limit", "20"));
  }

  @Test
  void testNoStackTraceOnInternalError() throws Exception {
    mockMvc.perform(get("/api/platform/users/test-error"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
        .andExpect(jsonPath("$.stack_trace").doesNotExist());
  }
}
