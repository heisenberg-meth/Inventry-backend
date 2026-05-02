package com.ims.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ims.BaseIntegrationTest;
import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.request.CustomerRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.CustomerResponse;
import com.ims.dto.response.ProductResponse;
import com.ims.dto.response.SignupResponse;
import com.ims.model.Customer;
import com.ims.shared.auth.SignupService;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.repository.CustomerRepository;
import com.ims.tenant.service.CustomerService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

public class BillingIntegrationTest extends BaseIntegrationTest {

        @Autowired
        private SignupService signupService;
        @Autowired
        private CustomerService customerService;
        @Autowired
        private CustomerRepository customerRepository;

        @Override
        @BeforeEach
        protected void setUp() throws Exception {
                super.setUp();
                cleanupDatabase();
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
                SignupResponse response = signupService.signup(signup);
                verifyUserEmail("admin@billing.com");
                verifyUser("admin@billing.com");

                Long tenantId = Objects
                                .requireNonNull(tenantRepository.findByWorkspaceSlug("billing-corp").orElseThrow()
                                                .getId());
                String token2 = login("admin@billing.com", "password123",
                                Objects.requireNonNull(response.getCompanyCode()),
                                tenantId);

                Customer customer;
                try {
                        TenantContext.setTenantId(tenantId);
                        CustomerRequest custReq = new CustomerRequest();
                        custReq.setName("Billing Customer");
                        custReq.setAddress("123 Street");
                        CustomerResponse custResponse = customerService.create(custReq);
                        customer = customerRepository.findById(Objects.requireNonNull(custResponse.getId()))
                                        .orElseThrow();
                } finally {
                        TenantContext.clear();
                }

                CreateProductRequest createReq = new CreateProductRequest();
                createReq.setName("Billing Product");
                createReq.setSku("BILL-001");
                createReq.setSalePrice(new BigDecimal("150.00"));
                String createReqJson = Objects.requireNonNull(objectMapper.writeValueAsString(createReq));
                MvcResult prodResult = mockMvc
                                .perform(
                                                post("/api/v1/tenant/products")
                                                                .header("Authorization", "Bearer " + token2)
                                                                .with(tenant(Objects.requireNonNull(
                                                                                String.valueOf(tenantId))))
                                                                .contentType(Objects.requireNonNull(
                                                                                MediaType.APPLICATION_JSON))
                                                                .content(createReqJson))
                                .andExpect(status().isCreated())
                                .andReturn();
                ProductResponse product = objectMapper.readValue(
                                prodResult.getResponse().getContentAsString(), ProductResponse.class);

                // Add initial stock
                String stockInJson = Objects.requireNonNull(
                                objectMapper.writeValueAsString(
                                                Map.of("product_id", product.getId(), "quantity", 100, "notes",
                                                                "Initial Stock")));
                mockMvc
                                .perform(
                                                post("/api/v1/tenant/stock/in")
                                                                .header("Authorization", "Bearer " + token2)
                                                                .with(tenant(Objects.requireNonNull(
                                                                                String.valueOf(tenantId))))
                                                                .contentType(Objects.requireNonNull(
                                                                                MediaType.APPLICATION_JSON))
                                                                .content(stockInJson))
                                .andExpect(status().isOk());

                // 2. Create Sales Order
                Map<String, Object> orderReq = Map.of(
                                "customer_id", customer.getId(),
                                "items",
                                List.of(
                                                Map.of("product_id", product.getId(), "quantity", 2, "unit_price",
                                                                150.00)));

                String orderReqJson = Objects.requireNonNull(objectMapper.writeValueAsString(orderReq));
                MvcResult orderResult = mockMvc
                                .perform(
                                                post("/api/v1/tenant/orders/sale")
                                                                .header("Authorization", "Bearer " + token2)
                                                                .with(tenant(Objects.requireNonNull(
                                                                                String.valueOf(tenantId))))
                                                                .contentType(Objects.requireNonNull(
                                                                                MediaType.APPLICATION_JSON))
                                                                .content(orderReqJson))
                                .andExpect(status().isCreated())
                                .andReturn();

                Map<String, Object> orderResponse = objectMapper.readValue(
                                orderResult.getResponse().getContentAsString(),
                                new TypeReference<Map<String, Object>>() {
                                });
                Object orderIdObj = orderResponse.get("id");
                if (orderIdObj == null) {
                    orderIdObj = orderResponse.get("order_id");
                }
                Long orderId = Long.valueOf(orderIdObj.toString());

                // 3. Confirm Order (Triggers Invoice Generation)
                mockMvc
                                .perform(
                                                post("/api/v1/tenant/orders/" + orderId + "/confirm")
                                                                .header("Authorization", "Bearer " + token2)
                                                                .with(tenant(Objects.requireNonNull(
                                                                                String.valueOf(tenantId)))))
                                .andExpect(status().isOk());

                // 4. Verify Invoice Exists
                MvcResult invoicesResult = mockMvc
                                .perform(
                                                get("/api/v1/tenant/invoices")
                                                                .header("Authorization", "Bearer " + token2)
                                                                .with(tenant(Objects.requireNonNull(
                                                                                String.valueOf(tenantId)))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content[0].orderId").value(orderId))
                                .andReturn();

                String invoicesJson = invoicesResult.getResponse().getContentAsString();
                Long invoiceId = objectMapper.readTree(invoicesJson).get("content").get(0).get("id").asLong();

                // 5. Download PDF
                mockMvc
                                .perform(
                                                get("/api/v1/tenant/invoices/" + invoiceId + "/pdf")
                                                                .header("Authorization", "Bearer " + token2)
                                                                .with(tenant(Objects.requireNonNull(
                                                                                String.valueOf(tenantId))))
                                                                .contentType(Objects.requireNonNull(
                                                                                MediaType.APPLICATION_PDF)))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(Objects.requireNonNull(MediaType.APPLICATION_PDF)))
                                .andExpect(
                                                header()
                                                                .string(
                                                                                "Content-Disposition",
                                                                                "attachment; filename=invoice-"
                                                                                                + invoiceId + ".pdf"));
        }
}
