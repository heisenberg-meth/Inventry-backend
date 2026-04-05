package com.ims.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.LoginResponse;
import com.ims.dto.response.ProductResponse;
import com.ims.platform.repository.TenantRepository;
import com.ims.shared.auth.SignupService;
import com.ims.tenant.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuditTrailIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private SignupService signupService;
  @Autowired private UserRepository userRepository;
  @Autowired private TenantRepository tenantRepository;

  @MockitoBean private RedisTemplate<String, Object> redisTemplate;
  @MockitoBean private ValueOperations<String, Object> valueOperations;
  @MockitoBean private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;
  @MockitoBean private org.springframework.cache.CacheManager cacheManager;
  @MockitoBean private org.springframework.cache.interceptor.CacheResolver tenantAwareCacheResolver;

  @BeforeEach
  void setup() {
    userRepository.deleteAll();
    tenantRepository.deleteAll();
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment(anyString())).thenReturn(1L);
    
    org.springframework.cache.Cache dummyCache = new org.springframework.cache.concurrent.ConcurrentMapCache("dummy");
    doReturn(java.util.Collections.singletonList(dummyCache)).when(tenantAwareCacheResolver).resolveCaches(any());
    when(cacheManager.getCache(anyString())).thenReturn(dummyCache);
  }

  @Test
  void testProductAuditTrail() throws Exception {
    // 1. Signup Tenant
    SignupRequest signup = createSignupRequest("Audit Corp", "audit-corp", "admin@audit.com");
    signupService.signup(signup);
    String token = login("admin@audit.com", "password123", "audit-corp");

    // 2. Create Product
    CreateProductRequest createReq = new CreateProductRequest();
    createReq.setName("Audit Product");
    createReq.setSku("AUDIT-001");
    createReq.setSalePrice(new BigDecimal("100.00"));

    MvcResult createResult = mockMvc.perform(post("/api/tenant/products")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createReq)))
        .andExpect(status().isCreated())
        .andReturn();

    ProductResponse product = objectMapper.readValue(createResult.getResponse().getContentAsString(), ProductResponse.class);

    // 3. Verify Audit Log for CREATE
    mockMvc.perform(get("/api/tenant/audits")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.action == 'CREATE' && @.details contains 'Audit Product')]").exists());

    // 4. Update Product
    createReq.setName("Updated Audit Product");
    mockMvc.perform(put("/api/tenant/products/" + product.getId())
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createReq)))
        .andExpect(status().isOk());

    // 5. Verify Audit Log for UPDATE
    mockMvc.perform(get("/api/tenant/audits")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.action == 'UPDATE' && @.details contains 'Updated Audit Product')]").exists());
  }

  @Test
  void testAuditIsolation() throws Exception {
    // 1. Signup Tenant 1
    SignupRequest t1Signup = createSignupRequest("T1", "t1", "admin@t1.com");
    signupService.signup(t1Signup);
    String t1Token = login("admin@t1.com", "password123", "t1");

    // 2. Signup Tenant 2
    SignupRequest t2Signup = createSignupRequest("T2", "t2", "admin@t2.com");
    signupService.signup(t2Signup);
    String t2Token = login("admin@t2.com", "password123", "t2");

    // 3. T1 performs an action
    CreateProductRequest createReq = new CreateProductRequest();
    createReq.setName("T1 Product");
    createReq.setSku("T1-001");
    mockMvc.perform(post("/api/tenant/products")
            .header("Authorization", "Bearer " + t1Token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createReq)))
        .andExpect(status().isCreated());

    // 4. Verify T1 sees their audit
    mockMvc.perform(get("/api/tenant/audits")
            .header("Authorization", "Bearer " + t1Token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.details contains 'T1 Product')]").exists());

    // 5. Verify T2 DOES NOT see T1's audit
    mockMvc.perform(get("/api/tenant/audits")
            .header("Authorization", "Bearer " + t2Token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.details contains 'T1 Product')]").doesNotExist());
  }

  private SignupRequest createSignupRequest(String name, String workspaceSlug, String email) {
    SignupRequest req = new SignupRequest();
    req.setBusinessName(name);
    req.setBusinessType("RETAIL");
    req.setWorkspaceSlug(workspaceSlug);
    req.setOwnerName("Owner " + name);
    req.setOwnerEmail(email);
    req.setPassword("password123");
    return req;
  }

  private String login(String email, String password, String companyCode) throws Exception {
    LoginRequest loginRequest = new LoginRequest();
    loginRequest.setEmail(email);
    loginRequest.setPassword(password);
    loginRequest.setCompanyCode(companyCode);
    
    MvcResult result = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(loginRequest)))
        .andExpect(status().isOk())
        .andReturn();

    LoginResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), LoginResponse.class);
    return response.getAccessToken();
  }
}
