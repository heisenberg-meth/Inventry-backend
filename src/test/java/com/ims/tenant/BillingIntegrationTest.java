package com.ims.tenant;

import java.util.Objects;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ims.BaseIntegrationTest;
import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.response.ProductResponse;
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
import java.util.List;
import java.util.Map;
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
@SuppressWarnings("null")
public class BillingIntegrationTest extends BaseIntegrationTest {

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
  void testInvoiceGenerationAndPdfDownload() throws Exception {
    // 1. Setup Tenant and Data
    SignupRequest signup = new SignupRequest();
    signup.setBusinessName("Billing Corp");
    signup.setWorkspaceSlug("billing-corp");
    signup.setBusinessType("RETAIL");
    signup.setOwnerName("Admin");
    signup.setOwnerEmail("admin@billing.com");
    signup.setPassword("password123");
    signup.setAddress("456 Business Park, Industrial Area");
    signup.setGstin("29ABCDE1234F1Z5");
    com.ims.dto.response.SignupResponse response = signupService.signup(signup);
    verifyUser("admin@billing.com");

    Long tenantId = tenantRepository.findByWorkspaceSlug("billing-corp").orElseThrow().getId();
    String token = login("admin@billing.com", "password123", response.getCompanyCode());

    Customer customer;
    try {
      TenantContext.setTenantId(tenantId);
      customer = Objects.requireNonNull(
          customerService.create(Customer.builder().name("Billing Customer").address("123 Street").build()));
    } finally {
      TenantContext.clear();
    }

    CreateProductRequest createReq = new CreateProductRequest();
    createReq.setName("Billing Product");
    createReq.setSku("BILL-001");
    createReq.setSalePrice(new BigDecimal("150.00"));
    String createReqJson = Objects.requireNonNull(objectMapper.writeValueAsString(createReq));
    MvcResult prodResult = mockMvc.perform(post("/api/tenant/products")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(createReqJson))
        .andExpect(status().isCreated())
        .andReturn();
    ProductResponse product = objectMapper.readValue(prodResult.getResponse().getContentAsString(),
        ProductResponse.class);

    // Add initial stock
    String stockInJson = Objects.requireNonNull(objectMapper.writeValueAsString(Map.of(
        "product_id", product.getId(),
        "quantity", 100,
        "notes", "Initial Stock")));
    mockMvc.perform(post("/api/tenant/stock/in")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(stockInJson))
        .andExpect(status().isOk());

    // 2. Create Sales Order
    Map<String, Object> orderReq = Map.of(
        "customer_id", customer.getId(),
        "items", List.of(Map.of(
            "product_id", product.getId(),
            "quantity", 2,
            "unit_price", 150.00)));

    String orderReqJson = Objects.requireNonNull(objectMapper.writeValueAsString(orderReq));
    MvcResult orderResult = mockMvc.perform(post("/api/tenant/orders/sale")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(orderReqJson))
        .andExpect(status().isCreated())
        .andReturn();

    Map<String, Object> orderResponse = objectMapper.readValue(orderResult.getResponse().getContentAsString(),
        new TypeReference<Map<String, Object>>() {
        });
    Long orderId = Long.valueOf(orderResponse.get("order_id").toString());

    // 3. Confirm Order (Triggers Invoice Generation)
    mockMvc.perform(post("/api/tenant/orders/" + orderId + "/confirm")
        .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    // 4. Verify Invoice Exists
    MvcResult invoicesResult = mockMvc.perform(get("/api/tenant/invoices")
        .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].orderId").value(orderId))
        .andReturn();

    String invoicesJson = invoicesResult.getResponse().getContentAsString();
    Long invoiceId = objectMapper.readTree(invoicesJson).get("content").get(0).get("id").asLong();

    // 5. Download PDF
    mockMvc.perform(get("/api/tenant/invoices/" + invoiceId + "/pdf")
        .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(header().string("Content-Disposition", "attachment; filename=invoice-" + invoiceId + ".pdf"));
  }

  private String login(String email, String password, String workspace) throws Exception {
    LoginRequest loginRequest = new LoginRequest();
    loginRequest.setEmail(email);
    loginRequest.setPassword(password);
    loginRequest.setCompanyCode(workspace);

    String loginJson = Objects.requireNonNull(objectMapper.writeValueAsString(loginRequest));
    MvcResult result = mockMvc.perform(post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(loginJson))
        .andExpect(status().isOk())
        .andReturn();
    String content = result.getResponse().getContentAsString();
    return objectMapper.readTree(content).get("accessToken").asText();
  }
}
