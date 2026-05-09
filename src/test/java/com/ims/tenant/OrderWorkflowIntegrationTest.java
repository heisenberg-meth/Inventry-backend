package com.ims.tenant;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ims.BaseIntegrationTest;
import com.ims.helper.TestDataFactory;
import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.ProductResponse;
import com.ims.dto.response.SignupResponse;
import com.ims.model.Customer;
import com.ims.shared.auth.SignupService;
import com.ims.tenant.service.CustomerService;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@org.springframework.security.test.context.support.WithMockUser(
    username = "admin",
    authorities = {
      "ADMIN",
      "ROLE_ADMIN",
      "create_product",
      "view_product",
      "update_product",
      "delete_product",
      "create_order",
      "view_order",
      "create_supplier",
      "view_supplier",
      "delete_supplier",
      "manage_stock",
      "view_stock"
    })
public class OrderWorkflowIntegrationTest extends BaseIntegrationTest {

  @Autowired private SignupService signupService;
  @Autowired private CustomerService customerService;

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

    // Need to fetch tenant outside of tenant-scoped context since tenantRepository
    // is tenant-scoped
    Long tenantId =
        withTenant(
            response.getTenantId(),
            () -> tenantRepository.findById(response.getTenantId()).orElseThrow().getId());

    String token = login(uniqueEmail, "password123", response.getCompanyCode());

    // Create customer
    Customer customer =
        withTenant(
            tenantId,
            () -> customerService.create(Customer.builder().name("Test Customer").build()));
    assertNotNull(customer.getId());

    // Create product
    CreateProductRequest createReq = new CreateProductRequest();
    createReq.setName("Test Product");
    createReq.setSku("PROD-" + UUID.randomUUID().toString().substring(0, 8));
    createReq.setSalePrice(new BigDecimal("100.00"));

    mockMvc
        .perform(
            post("/api/v1/tenant/products")
                .header("Authorization", "Bearer " + token)
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(createReq))))
        .andExpect(status().isCreated())
        .andReturn();
    // Verify product was created
    mockMvc
        .perform(get("/api/v1/tenant/products").header("Authorization", "Bearer " + token))
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

    MvcResult prodResult =
        mockMvc
            .perform(
                post("/api/v1/tenant/products")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq)))
            .andExpect(status().isCreated())
            .andReturn();

    ProductResponse product =
        objectMapper.readValue(
            prodResult.getResponse().getContentAsString(), ProductResponse.class);

    // Add stock
    String stockInPayload =
        String.format(
            """
                        {
                          "productId": %d,
                          "quantity": 10,
                          "notes": "Initial stock"
                        }
                        """,
            product.getId());

    mockMvc
        .perform(
            post("/api/v1/tenant/stock/in")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(stockInPayload))
        .andExpect(status().isOk());

    // Create Customer
    String customerReq =
        """
                {
                  "name": "Test Customer",
                  "email": "cust_flow@test.com"
                }
                """;
    MvcResult custResult =
        mockMvc
            .perform(
                post("/api/v1/tenant/customers")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(customerReq))
            .andExpect(status().isCreated())
            .andReturn();
    Map<String, Object> customer =
        objectMapper.readValue(
            custResult.getResponse().getContentAsString(),
            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    Number customerId = (Number) customer.get("id");

    // 2. Create Order
    String orderPayload =
        String.format(
            """
                        {
                          "type": "SALE",
                          "customerId": %d,
                          "items": [
                            {
                              "productId": %d,
                              "quantity": 5,
                              "unitPrice": 100.0
                            }
                          ]
                        }
                        """,
            customerId.longValue(), product.getId());

    MvcResult orderResult =
        mockMvc
            .perform(
                post("/api/v1/tenant/orders")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(orderPayload))
            .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
            .andExpect(status().isCreated())
            .andReturn();

    Map<String, Object> orderResponse =
        objectMapper.readValue(
            orderResult.getResponse().getContentAsString(),
            new com.fasterxml.jackson.core.type.TypeReference<>() {});
    Number orderId = (Number) orderResponse.get("id");

    // 3. Confirm Order (Hits stock movement)
    mockMvc
        .perform(
            post("/api/v1/tenant/orders/" + orderId + "/confirm")
                .header("Authorization", "Bearer " + token))
        .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
        .andExpect(status().isOk());
  }
}
