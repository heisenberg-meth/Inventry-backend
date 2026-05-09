package com.ims.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ims.BaseIntegrationTest;
import com.ims.TestDataFactory;
import com.ims.dto.CreateInvoiceRequest;
import com.ims.dto.InvoiceStatusRequest;
import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.SignupResponse;
import com.ims.model.Customer;
import com.ims.model.Invoice;
import com.ims.order.dto.CreateOrderRequest;
import com.ims.order.dto.OrderItemRequest;
import com.ims.order.entity.OrderType;
import com.ims.shared.auth.SignupService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.AssertionErrors;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class BillingIntegrationTest extends BaseIntegrationTest {

  @Autowired private SignupService signupService;

  @BeforeEach
  void setup() {
    cleanupDatabase();
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
    String slug = "unique-t1-billing-" + UUID.randomUUID().toString().substring(0, 4);

    SignupRequest signup = createSignupRequest("Unique Business 1", slug, uniqueEmail);
    signup.setAddress("456 Business Park, Industrial Area");
    signup.setGstin("29ABCDE1234F1Z5");

    SignupResponse response = signupService.signup(signup);
    verifyUser(uniqueEmail);

    String token = login(uniqueEmail, "password123", response.getCompanyCode());

    // Create product via DTO
    CreateProductRequest productRequest = new CreateProductRequest();
    productRequest.setName("Test Product");
    productRequest.setSku("PROD-" + UUID.randomUUID().toString().substring(0, 8));
    productRequest.setSalePrice(BigDecimal.valueOf(100));
    productRequest.setPurchasePrice(BigDecimal.valueOf(80));

    mockMvc
        .perform(
            post("/api/v1/tenant/products")
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
    String slug = "unique-t2-billing-" + UUID.randomUUID().toString().substring(0, 4);

    SignupRequest signup = createSignupRequest("Unique Business 2", slug, uniqueEmail);
    SignupResponse signupResponse = signupService.signup(signup);
    verifyUser(uniqueEmail);

    String token = login(uniqueEmail, "password123", signupResponse.getCompanyCode());
    String tenantIdStr = signupResponse.getTenantId().toString();

    // 1.5 Create Product and Customer using DTOs
    CreateProductRequest productReq = new CreateProductRequest();
    productReq.setName("Test Product");
    productReq.setSku("PROD-" + UUID.randomUUID().toString().substring(0, 8));
    productReq.setSalePrice(new BigDecimal("100.00"));
    productReq.setPurchasePrice(new BigDecimal("80.00"));

    MvcResult prodResult =
        mockMvc
            .perform(
                post("/api/v1/tenant/products")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Tenant-ID", tenantIdStr)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(productReq)))
            .andDo(print())
            .andExpect(status().isCreated())
            .andReturn();

    Map<String, Object> prodData =
        objectMapper.readValue(
            prodResult.getResponse().getContentAsString(), new TypeReference<>() {});
    Long productId = ((Number) prodData.get("id")).longValue();

    mockMvc
        .perform(
            post("/api/v1/tenant/stock/in")
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-ID", tenantIdStr)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"productId\": %d, \"quantity\": 10, "
                            + "\"notes\": \"Stock for billing test\"}",
                        productId)))
        .andDo(print())
        .andExpect(status().isOk());

    Customer customerObj =
        Customer.builder()
            .name("Test Customer")
            .email("cust_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com")
            .build();

    MvcResult custResult =
        mockMvc
            .perform(
                post("/api/v1/tenant/customers")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Tenant-ID", tenantIdStr)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(customerObj)))
            .andDo(print())
            .andExpect(status().isCreated())
            .andReturn();

    Map<String, Object> custData =
        objectMapper.readValue(
            custResult.getResponse().getContentAsString(), new TypeReference<>() {});
    Long customerId = ((Number) custData.get("id")).longValue();

    // 2. Create Order (Sale)
    OrderItemRequest itemReq =
        OrderItemRequest.builder()
            .productId(productId)
            .quantity(2)
            .unitPrice(new BigDecimal("100.00"))
            .build();

    CreateOrderRequest orderReq =
        CreateOrderRequest.builder()
            .type(OrderType.SALE)
            .customerId(customerId)
            .items(List.of(itemReq))
            .build();

    MvcResult orderResult =
        mockMvc
            .perform(
                post("/api/v1/tenant/orders")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Tenant-ID", tenantIdStr)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(orderReq)))
            .andDo(print())
            .andExpect(status().isCreated())
            .andReturn();

    Map<String, Object> orderData =
        objectMapper.readValue(
            orderResult.getResponse().getContentAsString(), new TypeReference<>() {});
    Long orderId = ((Number) orderData.get("id")).longValue();

    // 4. Create Invoice using DTO
    CreateInvoiceRequest invoiceReq = new CreateInvoiceRequest();
    invoiceReq.setOrderId(orderId);
    invoiceReq.setDueDate(LocalDate.now().plusDays(30));

    MvcResult invResult =
        mockMvc
            .perform(
                post("/api/v1/tenant/invoices")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Tenant-ID", tenantIdStr)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invoiceReq)))
            .andDo(print())
            .andReturn();

    if (invResult.getResponse().getStatus() != 201) {
      System.out.println(
          "INVOICE CREATION FAILED. Response: " + invResult.getResponse().getContentAsString());
    }

    AssertionErrors.assertEquals("Expected 201 Created", 201, invResult.getResponse().getStatus());

    Invoice invoice =
        objectMapper.readValue(invResult.getResponse().getContentAsString(), Invoice.class);
    Long invoiceId = invoice.getId();

    // 5. Verify Invoice Details
    mockMvc
        .perform(
            get("/api/v1/tenant/invoices/" + invoiceId)
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-ID", tenantIdStr))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderId").value(orderId))
        .andExpect(jsonPath("$.status").value("UNPAID"));

    // 6. Update Invoice Status
    InvoiceStatusRequest statusReq = new InvoiceStatusRequest();
    statusReq.setStatus("VOID");

    mockMvc
        .perform(
            patch("/api/v1/tenant/invoices/" + invoiceId + "/status")
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-ID", tenantIdStr)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(statusReq)))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("VOID"));
  }
}
