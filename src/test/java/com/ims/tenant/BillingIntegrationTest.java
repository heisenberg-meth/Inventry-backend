package com.ims.tenant;

import java.util.UUID;
import java.util.Map;
import java.util.List;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.ims.BaseIntegrationTest;
import com.ims.TestDataFactory;
import com.ims.dto.CreateInvoiceRequest;
import com.ims.model.Invoice;
import com.ims.product.Product;
import com.ims.shared.auth.SignupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class BillingIntegrationTest extends BaseIntegrationTest {

  @Autowired
  private SignupService signupService;

  @BeforeEach
  void setup() {
    cleanupDatabase();
    // Clean in proper order to handle foreign keys
    jdbcTemplate.execute("DELETE FROM invoices WHERE 1=1");
    jdbcTemplate.execute("DELETE FROM order_items WHERE 1=1");
    jdbcTemplate.execute("DELETE FROM orders WHERE 1=1");
    jdbcTemplate.execute("DELETE FROM customers WHERE 1=1");
    jdbcTemplate.execute("DELETE FROM products WHERE 1=1");
  }

  @Test
  void testProductCreation() throws Exception {
    // 1. Setup Tenant and Data
    String uniqueEmail = TestDataFactory.email();
    String slug = "unique-t1-billing";

    com.ims.dto.request.SignupRequest signup = createSignupRequest("Unique Business 1", slug, uniqueEmail);
    signup.setAddress("456 Business Park, Industrial Area");
    signup.setGstin("29ABCDE1234F1Z5");

    com.ims.dto.response.SignupResponse response = signupService.signup(signup);
    verifyUser(uniqueEmail);

    String token = login(uniqueEmail, "password123", response.getCompanyCode());

    // Create product via DTO
    com.ims.dto.request.CreateProductRequest productRequest = new com.ims.dto.request.CreateProductRequest();
    productRequest.setName("Test Product");
    productRequest.setSku("PROD-" + UUID.randomUUID().toString().substring(0, 8));
    productRequest.setSalePrice(BigDecimal.valueOf(100));
    productRequest.setPurchasePrice(BigDecimal.valueOf(80));

    mockMvc.perform(post("/api/v1/tenant/products")
        .header("Authorization", "Bearer " + token)
        .header("X-Tenant-ID", response.getTenantId().toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(productRequest)))
        .andDo(print())
        .andExpect(status().isCreated());
  }

  @Test
  void testInvoiceCreation() throws Exception {
    // 1. Setup Tenant
    String uniqueEmail = TestDataFactory.email();
    String slug = "unique-t2-billing";

    com.ims.dto.request.SignupRequest signup = createSignupRequest("Unique Business 2", slug, uniqueEmail);
    com.ims.dto.response.SignupResponse signupResponse = signupService.signup(signup);
    verifyUser(uniqueEmail);

    String token = login(uniqueEmail, "password123", signupResponse.getCompanyCode());
    String tenantIdStr = signupResponse.getTenantId().toString();

    // 1.5 Create Product and Customer
    // Use the Entity directly for creation via POST (simulating real usage)
    Product productObj = Product.builder()
        .name("Test Product")
        .sku("PROD-" + UUID.randomUUID().toString().substring(0, 8))
        .salePrice(new BigDecimal("100.00"))
        .purchasePrice(new BigDecimal("80.00"))
        .stock(100)
        .build();

    MvcResult prodResult = mockMvc.perform(post("/api/v1/tenant/products")
        .header("Authorization", "Bearer " + token)
        .header("X-Tenant-ID", tenantIdStr)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(productObj)))
        .andDo(print())
        .andExpect(status().isCreated())
        .andReturn();

    Map<String, Object> prodData = objectMapper.readValue(prodResult.getResponse().getContentAsString(),
        new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
    Long productId = ((Number) prodData.get("id")).longValue();

    mockMvc.perform(post("/api/v1/tenant/stock/in")
        .header("Authorization", "Bearer " + token)
        .header("X-Tenant-ID", tenantIdStr)
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            String.format("{\"productId\": %d, \"quantity\": 10, \"notes\": \"Stock for billing test\"}", productId)))
        .andDo(print())
        .andExpect(status().isOk());

    com.ims.model.Customer customerObj = com.ims.model.Customer.builder()
        .name("Test Customer")
        .email("cust_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com")
        .build();

    MvcResult custResult = mockMvc.perform(post("/api/v1/tenant/customers")
        .header("Authorization", "Bearer " + token)
        .header("X-Tenant-ID", tenantIdStr)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(customerObj)))
        .andDo(print())
        .andExpect(status().isCreated())
        .andReturn();

    Map<String, Object> custData = objectMapper.readValue(custResult.getResponse().getContentAsString(),
        new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
    Long customerId = ((Number) custData.get("id")).longValue();

    // 2. Create Order (Sale)
    com.ims.order.dto.OrderItemRequest itemReq = com.ims.order.dto.OrderItemRequest.builder()
        .productId(productId)
        .quantity(2)
        .unitPrice(new BigDecimal("100.00"))
        .build();

    com.ims.order.dto.CreateOrderRequest orderReq = com.ims.order.dto.CreateOrderRequest.builder()
        .type(com.ims.order.entity.OrderType.SALE)
        .customerId(customerId)
        .items(List.of(itemReq))
        .build();

    MvcResult orderResult = mockMvc.perform(post("/api/v1/tenant/orders")
        .header("Authorization", "Bearer " + token)
        .header("X-Tenant-ID", tenantIdStr)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(orderReq)))
        .andDo(print())
        .andExpect(status().isCreated())
        .andReturn();

    Map<String, Object> orderData = objectMapper.readValue(orderResult.getResponse().getContentAsString(),
        new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
    Long orderId = ((Number) orderData.get("id")).longValue();

    // 3. Confirm Order
    mockMvc.perform(post("/api/v1/tenant/orders/" + orderId + "/confirm")
        .header("Authorization", "Bearer " + token)
        .header("X-Tenant-ID", tenantIdStr))
        .andDo(print())
        .andExpect(status().isOk());

    // 4. Create Invoice
    CreateInvoiceRequest invoiceRequest = new CreateInvoiceRequest();
    invoiceRequest.setOrderId(orderId);
    invoiceRequest.setDueDate(LocalDate.now().plusDays(30));

    MvcResult invResult = mockMvc.perform(post("/api/v1/tenant/invoices")
        .header("Authorization", "Bearer " + token)
        .header("X-Tenant-ID", tenantIdStr)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(invoiceRequest)))
        .andDo(print())
        .andExpect(status().isCreated())
        .andReturn();

    Invoice invoice = objectMapper.readValue(invResult.getResponse().getContentAsString(), Invoice.class);
    Long invoiceId = invoice.getId();

    // 5. Verify Invoice Details
    mockMvc.perform(get("/api/v1/tenant/invoices/" + invoiceId)
        .header("Authorization", "Bearer " + token)
        .header("X-Tenant-ID", tenantIdStr))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderId").value(orderId))
        .andExpect(jsonPath("$.status").value("UNPAID"));
  }
}