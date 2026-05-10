package com.ims.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.dto.request.CreateProductRequest;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@Component
@RequiredArgsConstructor
public class ProductTestHelper {

  private final MockMvc mockMvc;
  private final ObjectMapper objectMapper;

  public void createProduct(String name, BigDecimal salePrice, String token, Long tenantId)
      throws Exception {
    CreateProductRequest productRequest = new CreateProductRequest();
    productRequest.setName(name);
    productRequest.setSku("PROD-" + UUID.randomUUID().toString().substring(0, 8));
    productRequest.setSalePrice(salePrice);
    productRequest.setPurchasePrice(salePrice.multiply(new BigDecimal("0.8")));

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/v1/tenant/products")
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-ID", tenantId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(productRequest)))
        .andExpect(MockMvcResultMatchers.status().isCreated());
  }
}
