package com.ims.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.ims.BaseIntegrationTest;
import com.ims.dto.CreateInvoiceRequest;
import com.ims.dto.InvoiceStatusRequest;
import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.SignupResponse;
import com.ims.helper.TestDataFactory;
import com.ims.model.Customer;
import com.ims.model.Invoice;
import com.ims.order.dto.CreateOrderRequest;
import com.ims.order.dto.OrderItemRequest;
import com.ims.order.entity.OrderType;
import com.ims.shared.auth.SignupService;
import com.ims.tenant.service.InventoryService;
import java.math.BigDecimal;
import java.util.List;
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

    @Autowired
    private SignupService signupService;

    @Autowired
    private InventoryService inventoryService;

    @BeforeEach
    void setup() {
        // cleanupDatabase is called by superclass
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
        SignupResponse signupResponse = signupService.signup(signup);
        verifyUser(uniqueEmail);

        String token = login(uniqueEmail, "password123", signupResponse.getCompanyCode());
        String tenantIdStr = signupResponse.getTenantId().toString();

        // 2. Create Product
        CreateProductRequest productReq = new CreateProductRequest();
        productReq.setName("Test Product");
        productReq.setSku("PROD-" + UUID.randomUUID().toString().substring(0, 8));
        productReq.setSalePrice(new BigDecimal("100.00"));
        productReq.setPurchasePrice(new BigDecimal("80.00"));

        mockMvc
                .perform(
                        post("/api/v1/tenant/products")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Tenant-ID", tenantIdStr)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(productReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Product"));
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

        MvcResult prodResult = mockMvc
                .perform(
                        post("/api/v1/tenant/products")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Tenant-ID", tenantIdStr)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(productReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long productId = objectMapper.readTree(prodResult.getResponse().getContentAsString()).get("id").asLong();

        // Add initial stock properly via service
        inventoryService.increaseStock(Long.valueOf(tenantIdStr), productId, 100, "Initial stock", testUserId);

        // Create Customer
        Customer customer = Customer.builder()
                .name("Test Customer")
                .email("customer@test.com")
                .phone("1234567890")
                .build();

        MvcResult custResult = mockMvc
                .perform(
                        post("/api/v1/tenant/customers")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Tenant-ID", tenantIdStr)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(customer)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andReturn();

        Long customerId = objectMapper.readTree(custResult.getResponse().getContentAsString()).get("id").asLong();

        // 2. Create Order using DTO
        CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                .type(OrderType.SALE)
                .customerId(customerId)
                .items(List.of(OrderItemRequest.builder()
                        .productId(productId)
                        .quantity(2)
                        .unitPrice(new BigDecimal("100.00"))
                        .build()))
                .build();

        MvcResult orderResult = mockMvc
                .perform(
                        post("/api/v1/tenant/orders")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Tenant-ID", tenantIdStr)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(orderRequest)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andReturn();

        Long orderId = objectMapper.readTree(orderResult.getResponse().getContentAsString()).get("id").asLong();

        // 3. Create Invoice
        CreateInvoiceRequest invRequest = new CreateInvoiceRequest();
        invRequest.setOrderId(orderId);
        invRequest.setDueDate(java.time.LocalDate.now().plusDays(30));

        MvcResult invResult = mockMvc
                .perform(
                        post("/api/v1/tenant/invoices")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Tenant-ID", tenantIdStr)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invRequest)))
                .andDo(print())
                .andReturn();

        if (invResult.getResponse().getStatus() != 201) {
            System.out.println(
                    "INVOICE CREATION FAILED. Response: " + invResult.getResponse().getContentAsString());
        }

        AssertionErrors.assertEquals("Expected 201 Created", 201, invResult.getResponse().getStatus());

        Invoice invoice = objectMapper.readValue(invResult.getResponse().getContentAsString(), Invoice.class);
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
        statusReq.setStatus("PAID");

        mockMvc
                .perform(
                        patch("/api/v1/tenant/invoices/" + invoiceId + "/status")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Tenant-ID", tenantIdStr)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(statusReq)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }
}
