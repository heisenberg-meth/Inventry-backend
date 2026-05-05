package com.ims.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.TestDataFactory;
import com.ims.BaseIntegrationTest;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.SignupResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SignupLoginTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void signupThenLogin() throws Exception {
        String uniqueEmail = TestDataFactory.email();
        String uniqueSlug = TestDataFactory.slug();

        SignupRequest signupRequest = new SignupRequest();
        signupRequest.setBusinessName(TestDataFactory.business());
        signupRequest.setWorkspaceSlug(uniqueSlug);
        signupRequest.setBusinessType("RETAIL");
        signupRequest.setOwnerName("Test Owner");
        signupRequest.setOwnerEmail(uniqueEmail);
        signupRequest.setPassword("password123");

        MvcResult signupResult = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        SignupResponse signupResponse = objectMapper.readValue(
                signupResult.getResponse().getContentAsString(), SignupResponse.class);

        verifyUser(uniqueEmail);

        // 3. Login after verification (should succeed)
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(uniqueEmail);
        loginRequest.setPassword("password123");
        loginRequest.setCompanyCode(signupResponse.getCompanyCode());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    void loginWithWrongPasswordShouldReturn401() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("nonexistent@test.com");
        loginRequest.setPassword("wrong");
        loginRequest.setCompanyCode("INVALID");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());  // 401
    }
}
