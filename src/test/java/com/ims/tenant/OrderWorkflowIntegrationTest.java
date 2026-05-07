package com.ims.tenant;

import java.util.Objects;
import java.util.UUID;
import java.util.Map;

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
import com.ims.dto.response.ProductResponse;
import com.ims.model.Customer;
import com.ims.shared.auth.SignupService;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import java.math.BigDecimal;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc

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
  }

  @Test
  void testProductAndCustomerCreation() throws Exception {
    // 1. Setup Tenant and Data
    String uniqueEmail = TestDataFactory.email();

    SignupRequest signup = new SignupRequest();
    signup.setBusinessName(TestDataFactory.business());
    signup.setBusinessType("RETAIL");
    signup.setOwnerName("Admin");
    signup.setOwnerEmail(uniqueEmail);
    signup.setPassword("password123");
    SignupResponse response = signupService.signup(signup);
    verifyUser(uniqueEmail);

    // Need to fetch tenant outside of tenant-scoped context since tenantRepository is tenant-scoped
    TenantContext.setTenantId(response.getTenantId());
    Long tenantId = tenantRepository.findById(response.getTenantId()).orElseThrow().getId();
    TenantContext.clear();

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

  @Test
  void testOrderConfirmationFlow() throws Exception {
    // 1. Setup Tenant and Product
    String uniqueEmail = TestDataFactory.email();
    SignupRequest signup = new SignupRequest();
    signup.setBusinessName(TestDataFactory.business());
    signup.setBusinessType("RETAIL");
    signup.setOwnerName("Admin");
    signup.setOwnerEmail(uniqueEmail);
    signup.setPassword("password123");
    SignupResponse signupResponse = signupService.signup(signup);
    verifyUser(uniqueEmail);
    String token = login(uniqueEmail, "password123", signupResponse.getCompanyCode());

    // Create product
    CreateProductRequest createReq = new CreateProductRequest();
    createReq.setName("Test Product");
    createReq.setSku("SKU-" + UUID.randomUUID().toString().substring(0, 8));
    createReq.setSalePrice(new BigDecimal("100.00"));

    MvcResult prodResult = mockMvc.perform(post("/api/tenant/products")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(createReq)))
        .andExpect(status().isCreated())
        .andReturn();

    ProductResponse product = objectMapper.readValue(prodResult.getResponse().getContentAsString(),
        ProductResponse.class);

    // Add stock
    String stockInPayload = String.format("""
        {
          "productId": %d,
          "quantity": 10,
          "notes": "Initial stock"
        }
        """, product.getId());

    mockMvc.perform(post("/api/tenant/stock/in")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(stockInPayload))
        .andExpect(status().isOk());

    // Create Customer
    String customerReq = """
        {
          "name": "Test Customer",
          "email": "cust_flow@test.com"
        }
        """;
    MvcResult custResult = mockMvc.perform(post("/api/tenant/customers")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(customerReq))
        .andExpect(status().isCreated())
        .andReturn();
    @SuppressWarnings("unchecked")
    Map<String, Object> customer = objectMapper.readValue(custResult.getResponse().getContentAsString(), Map.class);
    Number customerId = (Number) customer.get("id");

    // 2. Create Order
    String orderPayload = String.format("""
        {
          "customer_id": %d,
          "items": [
            {
              "product_id": %d,
              "quantity": 5,
              "unit_price": 100.0
            }
          ]
        }
        """, customerId.longValue(), product.getId());

    MvcResult orderResult = mockMvc.perform(post("/api/tenant/orders/sale")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(orderPayload))
        .andExpect(status().isCreated())
        .andReturn();

    Map<String, Object> orderResponse = objectMapper.readValue(orderResult.getResponse().getContentAsString(),
        new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
    Number orderId = (Number) orderResponse.get("order_id");

    // 3. Confirm Order (Hits stock movement)
    mockMvc.perform(post("/api/tenant/orders/" + orderId + "/confirm")
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