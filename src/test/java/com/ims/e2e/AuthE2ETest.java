package com.ims.e2e;

import com.ims.BaseIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.SignupResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
        cleanupDatabase();
        mockRedisAndCache();

        String uniqueEmail = "e2e-" + System.currentTimeMillis() + "@test.com";
        String uniqueSlug = "e2e-" + System.currentTimeMillis();
        
        SignupRequest signupRequest = new SignupRequest();
        signupRequest.setBusinessName("E2E Test Biz");
        signupRequest.setWorkspaceSlug(uniqueSlug);
        signupRequest.setBusinessType("RETAIL");
        signupRequest.setOwnerName("E2E Owner");
        signupRequest.setOwnerEmail(uniqueEmail);
        signupRequest.setPassword("password123");

        MvcResult signupResult = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        SignupResponse signupResponse = objectMapper.readValue(
                signupResult.getResponse().getContentAsString(),
                SignupResponse.class
        );

        verifyUser(uniqueEmail);

        String companyCode = signupResponse.getCompanyCode();

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(uniqueEmail);
        loginRequest.setPassword(signupRequest.getPassword());
        loginRequest.setCompanyCode(companyCode);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.expiresIn").exists());
    }
}