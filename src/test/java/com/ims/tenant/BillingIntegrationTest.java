package com.ims.tenant;

import java.util.Objects;
import java.util.UUID;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
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
import java.math.BigDecimal;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc

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
  }

  @Test
  void testProductCreation() throws Exception {
    // 1. Setup Tenant and Data
    String uniqueEmail = TestDataFactory.email();

    SignupRequest signup = new SignupRequest();
    signup.setBusinessName(TestDataFactory.business());
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

    mockMvc.perform(post("/api/v1/tenant/products")
        .header("Authorization", "Bearer " + token)
        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
        .content(Objects.requireNonNull(objectMapper.writeValueAsString(productRequest))))
        .andDo(print())
        .andExpect(status().isCreated());
  }

  @Test
  void testInvoiceCreation() throws Exception {
    // 1. Setup Tenant
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

    // 1.5 Create Product and Customer
    String productReq = """
        {
          "name": "Test Product",
          "sku": "PROD-BILL-1",
          "salePrice": 100.0
        }
        """;
    MvcResult prodResult = mockMvc.perform(post("/api/v1/tenant/products")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(productReq))
        .andDo(print())
        .andExpect(status().isCreated())
        .andReturn();
    Map<String, Object> product = objectMapper.readValue(prodResult.getResponse().getContentAsString(),
        new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
    Number productId = (Number) product.get("id");

    mockMvc.perform(post("/api/v1/tenant/stock/in")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(String.format("{\"productId\": %d, \"quantity\": 10, \"notes\": \"Stock for billing test\"}",
            productId.longValue())))
        .andDo(print())
        .andExpect(status().isOk());

    String customerReq = """
        {
          "name": "Test Customer",
          "email": "cust@test.com"
        }
        """;
    MvcResult custResult = mockMvc.perform(post("/api/v1/tenant/customers")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(customerReq))
        .andDo(print())
        .andExpect(status().isCreated())
        .andReturn();
    Map<String, Object> customer = objectMapper.readValue(custResult.getResponse().getContentAsString(),
        new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
    Number customerId = (Number) customer.get("id");

    // 2. Create Order (Sale)
    String orderPayload = String.format("""
        {
          "type": "SALE",
          "customerId": %d,
          "items": [
            {
              "productId": %d,
              "quantity": 2,
              "unitPrice": 100.0
            }
          ]
        }
        """, customerId.longValue(), productId.longValue());

    MvcResult orderResult = mockMvc.perform(post("/api/v1/tenant/orders")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(orderPayload))
        .andDo(print())
        .andExpect(status().isCreated())
        .andReturn();

    Map<String, Object> orderResponse = objectMapper.readValue(orderResult.getResponse().getContentAsString(),
        new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
    Number orderId = (Number) orderResponse.get("id"); // Usually 'id', not 'order_id'

    // Confirm the order to trigger invoice generation or allow manual invoice
    mockMvc.perform(post("/api/v1/tenant/orders/" + orderId.longValue() + "/confirm")
        .header("Authorization", "Bearer " + token))
        .andDo(print())
        .andExpect(status().isOk());

    // 3. Create Invoice
    String invoicePayload = String.format("""
        {
          "orderId": %d
        }
        """, orderId.longValue());

    mockMvc.perform(post("/api/v1/tenant/invoices")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(invoicePayload))
        .andDo(print())
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
        .andDo(print())
        .andExpect(status().isOk())
        .andReturn();

    String responseJson = result.getResponse().getContentAsString();
    com.ims.dto.response.LoginResponse loginResponse = objectMapper.readValue(responseJson,
        com.ims.dto.response.LoginResponse.class);
    return loginResponse.getAccessToken();
  }
}