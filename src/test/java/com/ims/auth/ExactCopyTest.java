package com.ims.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.BaseIntegrationTest;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.SignupResponse;
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

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
        "spring.cache.type=none",
        "app.security.allowed-origins=http://localhost:3000,http://localhost:5173"
})
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
        SignupRequest request = new SignupRequest();
        request.setBusinessName("Copy Biz");
        request.setWorkspaceSlug("copy-test");
        request.setBusinessType("RETAIL");
        request.setOwnerName("Copy Owner");
        request.setOwnerEmail("copy@test.com");
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()); // 201
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
                .andExpect(status().isNotFound()); // 404
    }

    @Test
    void signupThenVerifyThenLoginReturns200() throws Exception {
        // 1. Signup
        SignupRequest signupRequest = new SignupRequest();
        signupRequest.setBusinessName("Flow Biz");
        signupRequest.setWorkspaceSlug("flow-exact");
        signupRequest.setBusinessType("RETAIL");
        signupRequest.setOwnerName("Flow Owner");
        signupRequest.setOwnerEmail("flow-exact@test.com");
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
        verifyUser("flow-exact@test.com");

        // 3. Login after verification
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("flow-exact@test.com");
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
