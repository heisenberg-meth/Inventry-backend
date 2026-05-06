package com.ims.e2e;

import com.ims.BaseIntegrationTest;
import com.ims.TestDataFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.SignupResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthE2ETest extends BaseIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Test
        void fullAuthFlow_signupVerifyLogin_returnsAccessToken() throws Exception {
                // Arrange
                cleanupDatabase();
                mockRedisAndCache();

                SignupRequest signupRequest = createSignupRequest();

                // Act - Signup
                SignupResponse signupResponse = performSignup(signupRequest);

                verifyUser(signupRequest.getOwnerEmail());

                // Act - Login
                LoginRequest loginRequest = createLoginRequest(signupRequest, signupResponse.getCompanyCode());

                // Assert - Verify Login Response
                performLoginAndVerifyTokens(loginRequest);
        }

        private SignupRequest createSignupRequest() {
                SignupRequest signupRequest = new SignupRequest();
                signupRequest.setBusinessName(TestDataFactory.business());
                signupRequest.setBusinessType("RETAIL");
                signupRequest.setOwnerName("E2E Owner");
                signupRequest.setOwnerEmail(TestDataFactory.email());
                signupRequest.setPassword("password123");
                return signupRequest;
        }

        private LoginRequest createLoginRequest(SignupRequest signupRequest, String companyCode) {
                LoginRequest loginRequest = new LoginRequest();
                loginRequest.setEmail(signupRequest.getOwnerEmail());
                loginRequest.setPassword(signupRequest.getPassword());
                loginRequest.setCompanyCode(companyCode);
                return loginRequest;
        }

        private SignupResponse performSignup(SignupRequest signupRequest) throws Exception {
                String responseContent = mockMvc.perform(post("/api/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(signupRequest)))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                return objectMapper.readValue(responseContent, SignupResponse.class);
        }

        private void performLoginAndVerifyTokens(LoginRequest loginRequest) throws Exception {
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accessToken").exists())
                                .andExpect(jsonPath("$.refreshToken").exists())
                                .andExpect(jsonPath("$.expiresIn").exists());
        }
}