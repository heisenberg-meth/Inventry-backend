package com.ims.tenant;

import java.util.Objects;
import java.util.UUID;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.BaseIntegrationTest;
import com.ims.TestDataFactory;
import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.response.SignupResponse;
import com.ims.shared.auth.SignupService;
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

public class BillingIntegrationTest extends BaseIntegrationTest {

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private SignupService signupService;

  @BeforeEach
  void setup() {
    cleanupDatabase();
    mockRedisAndCache();
  }

  @Test
  void testProductCreation() throws Exception {
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
    signup.setAddress("456 Business Park, Industrial Area");
    signup.setGstin("29ABCDE1234F1Z5");
    SignupResponse response = signupService.signup(signup);
    verifyUser(uniqueEmail);

    String token = login(uniqueEmail, "password123", response.getCompanyCode());

    // Create product
    CreateProductRequest productRequest = new CreateProductRequest();
    productRequest.setName("Test Product");
    productRequest.setSku("PROD-" + UUID.randomUUID().toString().substring(0, 8));
    productRequest.setSalePrice(BigDecimal.valueOf(100));
    productRequest.setPurchasePrice(BigDecimal.valueOf(80));

    mockMvc.perform(post("/api/tenant/products")
        .header("Authorization", "Bearer " + token)
        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
        .content(Objects.requireNonNull(objectMapper.writeValueAsString(productRequest))))
        .andExpect(status().isCreated());
  }

  @Test
  void testInvoiceCreation() throws Exception {
    // 1. Setup Tenant
    String uniqueEmail = TestDataFactory.email();
    String uniqueSlug = TestDataFactory.slug();
    SignupRequest signup = new SignupRequest();
    signup.setBusinessName(TestDataFactory.business());
    signup.setWorkspaceSlug(uniqueSlug);
    signup.setBusinessType("RETAIL");
    signup.setOwnerName("Admin");
    signup.setOwnerEmail(uniqueEmail);
    signup.setPassword("password123");
    SignupResponse signupResponse = signupService.signup(signup);
    verifyUser(uniqueEmail);
    String token = login(uniqueEmail, "password123", signupResponse.getCompanyCode());

    // 1.5 Create Product and Customer
    String productReq = """
        {
          "name": "Test Product",
          "sku": "PROD-BILL-1",
          "salePrice": 100.0
        }
        """;
    MvcResult prodResult = mockMvc.perform(post("/api/tenant/products")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(productReq))
        .andExpect(status().isCreated())
        .andReturn();
     Map<String, Object> product = objectMapper.readValue(prodResult.getResponse().getContentAsString(),
         new com.fasterxml.jackson.core.type.TypeReference<>() {
         });
    Number productId = (Number) product.get("id");

    mockMvc.perform(post("/api/tenant/stock/in")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(String.format("{\"productId\": %d, \"quantity\": 10, \"notes\": \"Stock for billing test\"}",
            productId.longValue())))
        .andExpect(status().isOk());

    String customerReq = """
        {
          "name": "Test Customer",
          "email": "cust@test.com"
        }
        """;
    MvcResult custResult = mockMvc.perform(post("/api/tenant/customers")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(customerReq))
        .andExpect(status().isCreated())
        .andReturn();
     Map<String, Object> customer = objectMapper.readValue(custResult.getResponse().getContentAsString(),
         new com.fasterxml.jackson.core.type.TypeReference<>() {
         });
    Number customerId = (Number) customer.get("id");

    // 2. Create Order (Sale)
    String orderPayload = String.format("""
        {
          "customer_id": %d,
          "items": [
            {
              "product_id": %d,
              "quantity": 2,
              "unit_price": 100.0
            }
          ]
        }
        """, customerId.longValue(), productId.longValue());

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

    // 3. Create Invoice
    String invoicePayload = String.format("""
        {
          "orderId": %d
        }
        """, orderId.longValue());

    mockMvc.perform(post("/api/tenant/invoices")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(invoicePayload))
        .andExpect(status().isCreated());
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