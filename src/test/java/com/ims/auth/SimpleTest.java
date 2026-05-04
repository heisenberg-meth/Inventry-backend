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

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
        "spring.cache.type=none",
        "app.security.allowed-origins=http://localhost:3000,http://localhost:5173"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SimpleTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void signupReturns201() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setBusinessName("Simple Biz");
        request.setWorkspaceSlug("simple-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        request.setBusinessType("RETAIL");
        request.setOwnerName("Simple Owner");
        request.setOwnerEmail("simple-" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@test.com");
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void loginWithWrongPasswordReturns404() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("nonexistent@test.com");
        request.setPassword("wrong");
        request.setCompanyCode("INVALID");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound()); // 404 - EntityNotFoundException
    }

    @Test
    void signupThenVerifyThenLoginReturns200() throws Exception {
        String uid = java.util.UUID.randomUUID().toString().substring(0, 8);
        // 1. Signup
        SignupRequest signupRequest = new SignupRequest();
        signupRequest.setBusinessName("Flow Biz " + uid);
        signupRequest.setWorkspaceSlug("flow-" + uid);
        signupRequest.setBusinessType("RETAIL");
        signupRequest.setOwnerName("Flow Owner");
        signupRequest.setOwnerEmail("flow-" + uid + "@test.com");
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
        verifyUser("flow-" + uid + "@test.com");

        // 3. Login after verification
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("flow-" + uid + "@test.com");
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
