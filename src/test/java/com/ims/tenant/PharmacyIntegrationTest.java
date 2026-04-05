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
import com.ims.dto.request.UpdateTenantSettingsRequest;
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
import java.time.LocalDate;
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
public class PharmacyIntegrationTest {

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
  void testPharmacyProductFlow() throws Exception {
    // 1. Signup Pharmacy Tenant
    SignupRequest pharmacySignup = createSignupRequest("Pharma Life", "pharma", "admin@pharma.com", "PHARMACY");
    signupService.signup(pharmacySignup);
    String token = login("admin@pharma.com", "password123", "pharma");

    // 2. Create Pharmacy Product
    CreateProductRequest createReq = new CreateProductRequest();
    createReq.setName("Amoxicillin");
    createReq.setSku("AMOX-001");
    createReq.setSalePrice(new BigDecimal("150.00"));
    
    CreateProductRequest.PharmacyDetailsRequest pharmaDetails = new CreateProductRequest.PharmacyDetailsRequest();
    pharmaDetails.setBatchNumber("BATCH-123");
    pharmaDetails.setExpiryDate(LocalDate.now().plusDays(45).toString());
    pharmaDetails.setManufacturer("PharmaCorp");
    pharmaDetails.setSchedule("Schedule H");
    createReq.setPharmacyDetails(pharmaDetails);

    MvcResult createResult = mockMvc.perform(post("/api/tenant/products")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createReq)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Amoxicillin"))
        .andExpect(jsonPath("$.batch_number").value("BATCH-123"))
        .andExpect(jsonPath("$.schedule").value("Schedule H"))
        .andReturn();

    ProductResponse product = objectMapper.readValue(createResult.getResponse().getContentAsString(), ProductResponse.class);
    Long productId = product.getId();

    // 3. Update Pharmacy Product (Schedule change)
    pharmaDetails.setSchedule("Schedule H1");
    createReq.setName("Amoxicillin Forte");
    
    mockMvc.perform(put("/api/tenant/products/" + productId)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Amoxicillin Forte"))
        .andExpect(jsonPath("$.schedule").value("Schedule H1"));

    // 4. Verify Expiry Alerts (Default 30 days, product expires in 45 days -> should NOT appear)
    mockMvc.perform(get("/api/tenant/products/expiring")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    // 5. Update Tenant Expiry Threshold to 60 days
    UpdateTenantSettingsRequest settingsReq = new UpdateTenantSettingsRequest();
    settingsReq.setExpiryThresholdDays(60);
    
    mockMvc.perform(patch("/api/tenant/settings")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(settingsReq)))
        .andExpect(status().isOk());

    // 6. Verify Expiry Alerts (Threshold 60 days, product expires in 45 days -> should appear)
    mockMvc.perform(get("/api/tenant/products/expiring")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("Amoxicillin Forte"));
  }

  @Test
  void testPharmacyIsolation() throws Exception {
    // 1. Signup Retail Tenant
    SignupRequest retailSignup = createSignupRequest("Retail Store", "retail", "admin@retail.com", "RETAIL");
    signupService.signup(retailSignup);
    String token = login("admin@retail.com", "password123", "retail");

    // 2. Attempt to access pharmacy-only endpoint
    mockMvc.perform(get("/api/tenant/products/expiring")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Expiring products endpoint is only available for PHARMACY tenants"));
  }

  private SignupRequest createSignupRequest(String name, String workspaceSlug, String email, String type) {
    SignupRequest req = new SignupRequest();
    req.setBusinessName(name);
    req.setBusinessType(type);
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