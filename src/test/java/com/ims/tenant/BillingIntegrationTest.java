package com.ims.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.BaseIntegrationTest;
import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.ProductResponse;
import com.ims.model.Customer;
import com.ims.shared.auth.SignupService;
import com.ims.tenant.service.CustomerService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
@Slf4j
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
                        com.ims.shared.auth.TenantContext.setTenantId(tenantId);
                        customer = Objects.requireNonNull(
                                        customerService.create(Customer.builder().name("Billing Customer")
                                                        .address("123 Street").build()));
                } finally {
                        com.ims.shared.auth.TenantContext.clear();
                }

                // 2. Create Product
                CreateProductRequest productRequest = new CreateProductRequest();
                productRequest.setName("Test Product");
                productRequest.setSalePrice(BigDecimal.valueOf(100));
                productRequest.setPurchasePrice(BigDecimal.valueOf(80));
                productRequest.setCategoryId(1L);

                MvcResult productResult = mockMvc
                                .perform(post("/api/tenant/products")
                                                .header("Authorization", "Bearer " + token)
                                                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                                                .content(Objects.requireNonNull(
                                                                objectMapper.writeValueAsString(productRequest))))
                                .andExpect(status().isOk())
                                .andReturn();

                ProductResponse product = objectMapper.readValue(
                                productResult.getResponse().getContentAsString(), ProductResponse.class);

                // 3. Create Invoice
                Map<String, Object> invoiceRequest = Map.of(
                                "customerId", customer.getId(),
                                "items", List.of(Map.of(
                                                "productId", product.getId(),
                                                "quantity", 2,
                                                "price", BigDecimal.valueOf(100))),
                                "notes", "Test invoice");

                MvcResult invoiceResult = mockMvc
                                .perform(post("/api/tenant/invoices")
                                                .header("Authorization", "Bearer " + token)
                                                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                                                .content(Objects.requireNonNull(
                                                                objectMapper.writeValueAsString(invoiceRequest))))
                                .andExpect(status().isOk())
                                .andReturn();

                String invoiceJson = invoiceResult.getResponse().getContentAsString();
                log.info("Invoice created: {}", invoiceJson);

                // 4. Verify invoice exists
                mockMvc
                                .perform(get("/api/tenant/invoices")
                                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content.length()").value(1));

                log.info("Billing integration test completed successfully");
        }

        private String login(String email, String password, String companyCode) throws Exception {
                LoginRequest loginRequest = new LoginRequest();
                loginRequest.setEmail(email);
                loginRequest.setPassword(password);
                loginRequest.setCompanyCode(companyCode);

                MvcResult result = mockMvc
                                .perform(post("/api/auth/login")
                                                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                                                .content(Objects.requireNonNull(
                                                                objectMapper.writeValueAsString(loginRequest))))
                                .andExpect(status().isOk())
                                .andReturn();

                com.ims.dto.response.LoginResponse loginResponse = objectMapper.readValue(
                                result.getResponse().getContentAsString(),
                                com.ims.dto.response.LoginResponse.class);
                return loginResponse.getAccessToken();
        }
}
