package com.ims.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RealSignupLoginTest extends BaseIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Test
        void signupReturns201() throws Exception {
                SignupRequest request = new SignupRequest();
                request.setBusinessName("Test Business " + java.util.UUID.randomUUID().toString());
                request.setWorkspaceSlug("test-" + java.util.UUID.randomUUID().toString().substring(0, 8));
                request.setBusinessType("RETAIL");
                request.setOwnerName("Test Owner");
                request.setOwnerEmail("test-" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@test.com");
                request.setPassword("password123");

                mockMvc.perform(post("/api/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated()); // 201
        }

        @Test
        void loginWithWrongPasswordReturns401() throws Exception {
                LoginRequest request = new LoginRequest();
                request.setEmail("nonexistent-" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@test.com");
                request.setPassword("wrong");
                request.setCompanyCode("INVALID");

                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isUnauthorized()); // 401
        }

        @Test
        void signupThenVerifyThenLoginReturns200() throws Exception {
                String uniqueId = java.util.UUID.randomUUID().toString();
                String email = "flow-" + uniqueId.substring(0, 8) + "@test.com";
                String slug = "flow-" + uniqueId.substring(0, 8);

                // 1. Signup
                SignupRequest signupRequest = new SignupRequest();
                signupRequest.setBusinessName("Flow Test Biz " + uniqueId);
                signupRequest.setWorkspaceSlug(slug);
                signupRequest.setBusinessType("RETAIL");
                signupRequest.setOwnerName("Flow Owner");
                signupRequest.setOwnerEmail(email);
                signupRequest.setPassword("password123");

                MockHttpServletRequestBuilder signupBuilder = post("/api/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(signupRequest));
                MvcResult signupResult = mockMvc.perform(signupBuilder)
                                .andExpect(status().isCreated())
                                .andReturn();

                SignupResponse signupResponse = objectMapper.readValue(
                                signupResult.getResponse().getContentAsString(), SignupResponse.class);

                // 2. Verify user
                verifyUser(email);

                // 3. Login succeeds (200 OK)
                LoginRequest loginRequest = new LoginRequest();
                loginRequest.setEmail(email);
                loginRequest.setPassword("password123");
                loginRequest.setCompanyCode(signupResponse.getCompanyCode());

                MockHttpServletRequestBuilder loginBuilder = post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest));
                mockMvc.perform(loginBuilder)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accessToken").exists());
        }
}
