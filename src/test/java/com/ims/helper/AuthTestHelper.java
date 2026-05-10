package com.ims.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;

@Component
@RequiredArgsConstructor
public class AuthTestHelper {

  private final ObjectMapper objectMapper;
  private MockMvc mockMvc;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  public void setMockMvc(MockMvc mockMvc) {
    this.mockMvc = mockMvc;
  }

  public String login(String email, String password, String companyCode) throws Exception {
    LoginRequest loginRequest = new LoginRequest();
    loginRequest.setEmail(email);
    loginRequest.setPassword(password);
    loginRequest.setCompanyCode(companyCode);

    String loginJson = objectMapper.writeValueAsString(loginRequest);
    if (mockMvc == null) {
      throw new IllegalStateException(
          "MockMvc is not available. Use direct HTTP client or Playwright for login in E2E tests.");
    }
    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginJson))
            .andDo(MockMvcResultHandlers.print())
            .andReturn();

    String responseJson = result.getResponse().getContentAsString();
    if (responseJson == null || responseJson.isBlank()) {
      throw new IllegalStateException(
          "Login response body is empty for user: "
              + email
              + ", status: "
              + result.getResponse().getStatus());
    }
    LoginResponse response = objectMapper.readValue(responseJson, LoginResponse.class);
    return response.getAccessToken();
  }

  public SignupRequest createSignupRequest(String name, String slug, String email) {
    SignupRequest req = new SignupRequest();
    req.setBusinessName(name);
    req.setBusinessType("RETAIL");
    req.setOwnerName("Owner " + name);
    req.setOwnerEmail(email);
    req.setPassword("password123");
    req.setWorkspaceSlug(slug);
    return req;
  }
}
