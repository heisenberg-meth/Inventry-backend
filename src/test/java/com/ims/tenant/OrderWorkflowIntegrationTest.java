package com.ims.tenant;

import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.BaseIntegrationTest;
import com.ims.TestDataFactory;
import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.response.SignupResponse;
import com.ims.model.Customer;
import com.ims.shared.auth.SignupService;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import java.math.BigDecimal;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
    "spring.cache.type=none"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")

public class OrderWorkflowIntegrationTest extends BaseIntegrationTest {

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private SignupService signupService;
  @Autowired
  private CustomerService customerService;

  @BeforeEach
  void setup() {
    cleanupDatabase();
    mockRedisAndCache();
  }

  @Test
  void testProductAndCustomerCreation() throws Exception {
    // 1. Setup Tenant and Data
    String uniqueEmail = TestDataFactory.email();
    String uniqueSlug = TestDataFactory.slug();

    SignupRequest signup = new SignupRequest();
    signup.setBusinessName(TestDataFactory.business());
    signup.setWorkspaceSlug(uniqueSlug);
    signup.setBusinessType("RETAIL");
    signup.setOwnerName("Admin");
    signup.setOwnerEmail(uniqueEmail);
    signup.setPassword("password123");
    SignupResponse response = signupService.signup(signup);
    verifyUser(uniqueEmail);

    Long tenantId = tenantRepository.findByWorkspaceSlug(uniqueSlug).orElseThrow().getId();
    String token = login(uniqueEmail, "password123", response.getCompanyCode());

    // Create customer
    TenantContext.setTenantId(tenantId);
    Customer customer = customerService.create(Customer.builder().name("Test Customer").build());
    assertNotNull(customer.getId());
    TenantContext.clear();

    // Create product
    CreateProductRequest createReq = new CreateProductRequest();
    createReq.setName("Test Product");
    createReq.setSku("PROD-" + UUID.randomUUID().toString().substring(0, 8));
    createReq.setSalePrice(new BigDecimal("100.00"));

    mockMvc.perform(post("/api/tenant/products")
        .header("Authorization", "Bearer " + token)
        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
        .content(Objects.requireNonNull(objectMapper.writeValueAsString(createReq))))
        .andExpect(status().isCreated())
        .andReturn();
    // Verify product was created
    mockMvc.perform(get("/api/tenant/products")
        .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  private String login(String email, String password, String workspace) throws Exception {
    LoginRequest loginRequest = new LoginRequest();
    loginRequest.setEmail(email);
    loginRequest.setPassword(password);
    loginRequest.setCompanyCode(workspace);

    MvcResult result = mockMvc.perform(post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(loginRequest)))
        .andExpect(status().isOk())
        .andReturn();

    String responseJson = result.getResponse().getContentAsString();
    com.ims.dto.response.LoginResponse loginResponse = objectMapper.readValue(responseJson,
        com.ims.dto.response.LoginResponse.class);
    return loginResponse.getAccessToken();
  }
}