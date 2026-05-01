package com.ims.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.ims.BaseIntegrationTest;
import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.ProductResponse;
import com.ims.dto.response.SignupResponse;
import com.ims.shared.auth.SignupService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

public class PharmacyIntegrationTest extends BaseIntegrationTest {

        @Autowired
        private SignupService signupService;

        @Override
        @BeforeEach
        protected void setUp() throws Exception {
                super.setUp();
                cleanupDatabase();
        }

        @Test
        void testPharmacyProductFlow() throws Exception {
                SignupRequest signup = new SignupRequest();
                signup.setBusinessName("Pharmacy Corp");
                signup.setWorkspaceSlug("pharmacy-corp");
                signup.setBusinessType("PHARMACY");
                signup.setOwnerName("Admin");
                signup.setOwnerEmail("admin@pharmacy.com");
                signup.setPassword("password123");
                SignupResponse response = signupService.signup(signup);
                verifyUserEmail("admin@pharmacy.com");
                verifyUser("admin@pharmacy.com");
                Long tId = Objects.requireNonNull(
                                tenantRepository.findByWorkspaceSlug("pharmacy-corp").orElseThrow().getId());
                String token = login("admin@pharmacy.com", "password123",
                                Objects.requireNonNull(response.getCompanyCode()),
                                tId);

                // 1. Create Pharmacy Product
                CreateProductRequest createReq = new CreateProductRequest();
                createReq.setName("Paracetamol");
                createReq.setSku("PARA-001");
                createReq.setSalePrice(new BigDecimal("10.00"));

                CreateProductRequest.PharmacyDetailsRequest pharm = new CreateProductRequest.PharmacyDetailsRequest();
                pharm.setBatchNumber("BATCH-123");
                pharm.setExpiryDate(Objects.requireNonNull(LocalDate.now().plusMonths(6).toString()));
                pharm.setManufacturer("Pharma Co");
                createReq.setPharmacyDetails(pharm);

                String createReqJson = objectMapper.writeValueAsString(createReq);
                MvcResult result = mockMvc
                                .perform(
                                                post("/api/v1/tenant/products")
                                                                .header("Authorization", "Bearer " + token)
                                                                .with(tenant(Objects
                                                                                .requireNonNull(String.valueOf(tId))))
                                                                .contentType(Objects.requireNonNull(
                                                                                MediaType.APPLICATION_JSON))
                                                                .content(Objects.requireNonNull(createReqJson)))
                                .andExpect(status().isCreated())
                                .andReturn();

                ProductResponse product = objectMapper.readValue(result.getResponse().getContentAsString(),
                                ProductResponse.class);
                assertThat(product.getBatchNumber()).isEqualTo("BATCH-123");

                // 2. Fetch expiring products
                mockMvc
                                .perform(
                                                get("/api/v1/tenant/products/expiring?days=200")
                                                                .header("Authorization", "Bearer " + token)
                                                                .with(tenant(Objects
                                                                                .requireNonNull(String.valueOf(tId)))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()",
                                                Objects.requireNonNull(Matchers.<Integer>greaterThanOrEqualTo(1))));
        }

        @Test
        void testExpiredProductInclusion() throws Exception {
                SignupRequest signup = new SignupRequest();
                signup.setBusinessName("Expired Corp");
                signup.setWorkspaceSlug("expired-corp");
                signup.setBusinessType("PHARMACY");
                signup.setOwnerName("Admin");
                signup.setOwnerEmail("admin@expired.com");
                signup.setPassword("password123");
                SignupResponse response = signupService.signup(signup);
                verifyUserEmail("admin@expired.com");
                verifyUser("admin@expired.com");

                Long tId = Objects.requireNonNull(
                                tenantRepository.findByWorkspaceSlug("expired-corp").orElseThrow().getId());
                String token = login("admin@expired.com", "password123",
                                Objects.requireNonNull(response.getCompanyCode()),
                                tId);

                // 1. Create ALREADY EXPIRED product
                CreateProductRequest expiredReq = new CreateProductRequest();
                expiredReq.setName("Old Medicine");
                expiredReq.setSku("OLD-001");
                expiredReq.setSalePrice(new BigDecimal("10.00"));

                CreateProductRequest.PharmacyDetailsRequest pharm = new CreateProductRequest.PharmacyDetailsRequest();
                pharm.setBatchNumber("EXPIRED-BATCH");
                pharm.setExpiryDate(Objects.requireNonNull(LocalDate.now().minusDays(10).toString())); // 10 days ago
                pharm.setManufacturer("Old Pharma");
                expiredReq.setPharmacyDetails(pharm);

                mockMvc
                                .perform(
                                                post("/api/v1/tenant/products")
                                                                .header("Authorization", "Bearer " + token)
                                                                .with(tenant(Objects
                                                                                .requireNonNull(String.valueOf(tId))))
                                                                .contentType(Objects.requireNonNull(
                                                                                MediaType.APPLICATION_JSON))
                                                                .content(Objects.requireNonNull(objectMapper
                                                                                .writeValueAsString(expiredReq))))
                                .andExpect(status().isCreated());

                // 2. Fetch expiring products (threshold 30 days)
                // Business rule implementation check: already expired items ARE included so
                // they can be
                // identified and removed from stock
                MvcResult result = mockMvc
                                .perform(
                                                get("/api/v1/tenant/products/expiring?days=30")
                                                                .header("Authorization", "Bearer " + token)
                                                                .with(tenant(Objects
                                                                                .requireNonNull(String.valueOf(tId)))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(1))
                                .andReturn();

                ProductResponse[] products = objectMapper.readValue(result.getResponse().getContentAsString(),
                                ProductResponse[].class);
                assertThat(products[0].getBatchNumber()).isEqualTo("EXPIRED-BATCH");
        }

        @Test
        void testMandatoryPharmacyFields() throws Exception {
                SignupRequest signup = new SignupRequest();
                signup.setBusinessName("Missing Fields Corp");
                signup.setWorkspaceSlug("missing-corp");
                signup.setBusinessType("PHARMACY");
                signup.setOwnerName("Admin");
                signup.setOwnerEmail("admin@missing.com");
                signup.setPassword("password123");
                SignupResponse response = signupService.signup(signup);
                verifyUserEmail("admin@missing.com");
                verifyUser("admin@missing.com");

                Long tId = Objects.requireNonNull(
                                tenantRepository.findByWorkspaceSlug("missing-corp").orElseThrow().getId());
                String token = login("admin@missing.com", "password123",
                                Objects.requireNonNull(response.getCompanyCode()),
                                tId);

                // Create Pharmacy Product with missing pharmacy_details
                CreateProductRequest invalidReq = new CreateProductRequest();
                invalidReq.setName("Missing Details");
                invalidReq.setSku("MISS-001");
                invalidReq.setSalePrice(new BigDecimal("10.00"));
                // pharmacyDetails is left null

                // Should fail with 400 or 500 depending on service validation
                mockMvc
                                .perform(
                                                post("/api/v1/tenant/products")
                                                                .header("Authorization", "Bearer " + token)
                                                                .with(tenant(Objects
                                                                                .requireNonNull(String.valueOf(tId))))
                                                                .contentType(Objects.requireNonNull(
                                                                                MediaType.APPLICATION_JSON))
                                                                .content(Objects.requireNonNull(objectMapper
                                                                                .writeValueAsString(invalidReq))))
                                .andExpect(status().isBadRequest());
        }
}
