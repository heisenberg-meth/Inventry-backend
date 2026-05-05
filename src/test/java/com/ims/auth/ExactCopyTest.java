package com.ims.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.BaseIntegrationTest;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.SignupResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExactCopyTest extends BaseIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @BeforeEach
        void setup() {
                cleanupDatabase();
                mockRedisAndCache();
        }

        @Test
        void signupReturns201() throws Exception {
                String uniqueEmail = "copy-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
                String uniqueSlug = "copy-" + UUID.randomUUID().toString().substring(0, 8);

                SignupRequest request = new SignupRequest();
                request.setBusinessName("Copy Biz");
                request.setWorkspaceSlug(uniqueSlug);
                request.setBusinessType("RETAIL");
                request.setOwnerName("Copy Owner");
                request.setOwnerEmail(uniqueEmail);
                request.setPassword("password123");

                mockMvc.perform(post("/api/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated()); // 201
        }

        @Test
        void loginWithWrongPasswordReturns401() throws Exception {
                LoginRequest request = new LoginRequest();
                request.setEmail("nonexistent-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com");
                request.setPassword("wrong");
                request.setCompanyCode("INVALID");

                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isUnauthorized()); // 401
        }

        @Test
        void signupThenVerifyThenLoginReturns200() throws Exception {
                String uniqueEmail = "flow-copy-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
                String uniqueSlug = "flow-copy-" + UUID.randomUUID().toString().substring(0, 8);

                // 1. Signup
                SignupRequest signupRequest = new SignupRequest();
                signupRequest.setBusinessName("Flow Biz");
                signupRequest.setWorkspaceSlug(uniqueSlug);
                signupRequest.setBusinessType("RETAIL");
                signupRequest.setOwnerName("Flow Owner");
                signupRequest.setOwnerEmail(uniqueEmail);
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
                verifyUser(uniqueEmail);

                // 3. Login after verification (200 OK)
                LoginRequest loginRequest = new LoginRequest();
                loginRequest.setEmail(uniqueEmail);
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