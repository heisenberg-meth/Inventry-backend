package com.ims.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.dto.request.AssignPermissionsRequest;
import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.LoginResponse;
import com.ims.dto.response.ProductResponse;
import com.ims.dto.response.UserResponse;
import com.ims.model.Permission;
import com.ims.model.Role;
import com.ims.platform.repository.TenantRepository;
import com.ims.shared.auth.SignupService;
import com.ims.tenant.repository.PermissionRepository;
import com.ims.tenant.repository.RoleRepository;
import com.ims.tenant.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.math.BigDecimal;
import java.util.List;
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
public class RbacIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private SignupService signupService;
  @Autowired private UserRepository userRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private PermissionRepository permissionRepository;

  @MockitoBean private RedisTemplate<String, Object> redisTemplate;
  @MockitoBean private ValueOperations<String, Object> valueOperations;
  @MockitoBean private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;
  @MockitoBean private org.springframework.cache.CacheManager cacheManager;
  @MockitoBean private org.springframework.cache.interceptor.CacheResolver tenantAwareCacheResolver;

  @BeforeEach
  void setup() {
    userRepository.deleteAll();
    roleRepository.deleteAll();
    tenantRepository.deleteAll();
    
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment(anyString())).thenReturn(1L);
    
    org.springframework.cache.Cache dummyCache = new org.springframework.cache.concurrent.ConcurrentMapCache("dummy");
    doReturn(java.util.Collections.singletonList(dummyCache)).when(tenantAwareCacheResolver).resolveCaches(any());
    when(cacheManager.getCache(anyString())).thenReturn(dummyCache);
  }

  @Test
  void testPermissionBasedAccess() throws Exception {
    // 1. Signup Tenant
    SignupRequest signup = createSignupRequest("RBAC Corp", "rbac-corp", "admin@rbac.com");
    signupService.signup(signup);
    String adminToken = login("admin@rbac.com", "password123", "rbac-corp");

    // 2. Create a STAFF user (no delete_product permission by default)
    mockMvc.perform(post("/api/tenant/users")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Staff User\",\"email\":\"staff@rbac.com\",\"password\":\"staff123\",\"role\":\"STAFF\"}"))
        .andExpect(status().isCreated());

    String staffToken = login("staff@rbac.com", "staff123", "rbac-corp");

    // 3. Create a product as Admin
    CreateProductRequest createReq = new CreateProductRequest();
    createReq.setName("RBAC Product");
    createReq.setSku("RBAC-001");
    createReq.setSalePrice(new BigDecimal("100.00"));
    MvcResult prodResult = mockMvc.perform(post("/api/tenant/products")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createReq)))
        .andExpect(status().isCreated())
        .andReturn();
    ProductResponse product = objectMapper.readValue(prodResult.getResponse().getContentAsString(), ProductResponse.class);

    // 4. Try to delete as STAFF (should fail)
    mockMvc.perform(delete("/api/tenant/products/" + product.getId())
            .header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isForbidden());

    // 5. Assign delete_product permission to STAFF user
    Permission deletePerm = permissionRepository.findByKey("delete_product").orElseThrow();
    Long staffUserId = userRepository.findByEmailUnfiltered("staff@rbac.com").orElseThrow().getId();

    AssignPermissionsRequest assignReq = new AssignPermissionsRequest();
    assignReq.setPermissionIds(List.of(deletePerm.getId()));
    
    mockMvc.perform(post("/api/tenant/users/" + staffUserId + "/permissions")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(assignReq)))
        .andExpect(status().isOk());

    // 6. Try to delete as STAFF again (should succeed now)
    mockMvc.perform(delete("/api/tenant/products/" + product.getId())
            .header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isNoContent());
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
