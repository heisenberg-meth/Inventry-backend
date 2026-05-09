package com.ims.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.BaseIntegrationTest;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.request.SignupRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class AuthControllerTest extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void shouldRejectUnauthorizedRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                                {
                                                    "email": "nonexistent@test.com",
                                                    "password": "wrongpassword",
                                                    "companyCode": "INVALID"
                                                }
                                                """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldLoginSuccessfully() throws Exception {
    // 1. Signup
    String uniqueEmail = com.ims.TestDataFactory.email();
    SignupRequest signupRequest = new SignupRequest();
    signupRequest.setBusinessName("Test Business");
    signupRequest.setBusinessType("RETAIL");
    signupRequest.setOwnerName("Test Owner");
    signupRequest.setOwnerEmail(uniqueEmail);
    signupRequest.setPassword("password123");

    MvcResult signupResult =
        mockMvc
            .perform(
                post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(signupRequest)))
            .andDo(print())
            .andExpect(status().isCreated())
            .andReturn();

    String signupJson = signupResult.getResponse().getContentAsString();
    com.ims.dto.response.SignupResponse signupResponse =
        objectMapper.readValue(signupJson, com.ims.dto.response.SignupResponse.class);

    // 2. Verify user and login
    verifyUser(uniqueEmail);

    LoginRequest loginRequest = new LoginRequest();
    loginRequest.setEmail(uniqueEmail);
    loginRequest.setPassword("password123");
    loginRequest.setCompanyCode(signupResponse.getCompanyCode());

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").exists())
        .andExpect(jsonPath("$.refreshToken").exists())
        .andExpect(jsonPath("$.tokenType").value("Bearer"));
  }

  @Test
  void shouldRejectInvalidToken() throws Exception {
    // Invalid token should return 401 (unauthorized)
    mockMvc
        .perform(post("/api/auth/logout").header("Authorization", "Bearer invalid_token_here"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectLoginWithWrongPassword() throws Exception {
    // Use a valid signup first to get a real company code
    String uniqueEmail = com.ims.TestDataFactory.email();
    SignupRequest signupRequest = new SignupRequest();
    signupRequest.setBusinessName("Test Biz");
    signupRequest.setBusinessType("RETAIL");
    signupRequest.setOwnerName("Owner");
    signupRequest.setOwnerEmail(uniqueEmail);
    signupRequest.setPassword("password123");

    MvcResult signupResult =
        mockMvc
            .perform(
                post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(signupRequest)))
            .andDo(print())
            .andExpect(status().isCreated())
            .andReturn();

    com.ims.dto.response.SignupResponse signupResponse =
        objectMapper.readValue(
            signupResult.getResponse().getContentAsString(),
            com.ims.dto.response.SignupResponse.class);

    // Now try login with wrong password for existing user
    LoginRequest loginRequest = new LoginRequest();
    loginRequest.setEmail(uniqueEmail);
    loginRequest.setPassword("wrongpassword");
    loginRequest.setCompanyCode(signupResponse.getCompanyCode());

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
        .andExpect(status().isUnauthorized());
  }
}
