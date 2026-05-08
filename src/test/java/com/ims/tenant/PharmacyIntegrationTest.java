package com.ims.tenant;

import java.util.Objects;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.ims.BaseIntegrationTest;
import com.ims.TestDataFactory;
import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.SignupResponse;
import com.ims.shared.auth.SignupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.http.MediaType;

@AutoConfigureMockMvc

@org.springframework.security.test.context.support.WithMockUser(username = "admin", authorities = { "ADMIN",
    "ROLE_ADMIN", "create_product", "view_product", "update_product", "delete_product", "create_order", "view_order",
    "create_supplier", "view_supplier", "delete_supplier", "manage_stock", "view_stock" })
public class PharmacyIntegrationTest extends BaseIntegrationTest {

  @Autowired
  private SignupService signupService;

  @BeforeEach
  void setup() {
    cleanupDatabase();
  }

  @Test
  void testPharmacyProductCreation() throws Exception {
    String uniqueEmail = TestDataFactory.email();

    SignupRequest signup = new SignupRequest();
    signup.setBusinessName(TestDataFactory.business());
    signup.setBusinessType("PHARMACY");
    signup.setOwnerName("Admin");
    signup.setOwnerEmail(uniqueEmail);
    signup.setPassword("password123");
    SignupResponse response = signupService.signup(signup);
    verifyUser(uniqueEmail);
    String token = login(uniqueEmail, "password123", response.getCompanyCode());

    // Create Pharmacy Product
    CreateProductRequest createReq = new CreateProductRequest();
    createReq.setName("Paracetamol");
    createReq.setSku("PARA-" + UUID.randomUUID().toString().substring(0, 8));
    createReq.setSalePrice(new BigDecimal("10.00"));

    CreateProductRequest.PharmacyDetailsRequest pharm = new CreateProductRequest.PharmacyDetailsRequest();
    pharm.setBatchNumber("BATCH-" + UUID.randomUUID().toString().substring(0, 8));
    pharm.setExpiryDate(LocalDate.now().plusMonths(6).toString());
    pharm.setManufacturer("Pharma Co");
    createReq.setPharmacyDetails(pharm);

    mockMvc.perform(post("/api/v1/tenant/products")
        .header("Authorization", "Bearer " + token)
        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
        .content(Objects.requireNonNull(objectMapper.writeValueAsString(createReq))))
        .andExpect(status().isCreated());
  }

}