package com.ims.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ims.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class CustomerIntegrationTest extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void getCustomers_withoutAuth_returns401() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/tenant/customers")
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.anonymous()))
        .andExpect(status().isUnauthorized());
  }
}
