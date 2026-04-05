package com.ims.management;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.CreatePlatformUserRequest;
import com.ims.dto.response.LoginResponse;
import com.ims.model.User;
import com.ims.platform.repository.TenantRepository;
import com.ims.shared.auth.SignupService;
import com.ims.tenant.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.Objects;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ManagementIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private SignupService signupService;
  @Autowired private UserRepository userRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @MockitoBean private RedisTemplate<String, Object> redisTemplate;
  @MockitoBean private ValueOperations<String, Object> valueOperations;
  @MockitoBean private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;

  @BeforeEach
  void setup() {
    userRepository.deleteAll();
    tenantRepository.deleteAll();
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    // Create Root Admin
    User root =
        User.builder()
            .name("Root Admin")
            .email("root@ims.com")
            .passwordHash(passwordEncoder.encode("root123"))
            .role("ROOT")
            .scope("PLATFORM")
            .tenantId(0L)
            .isActive(true)
            .build();
    userRepository.save(Objects.requireNonNull(root));
  }

  @Test
  void testPlatformAdminFlow() throws Exception {
    String rootToken = login("root@ims.com", "root123");

    // ROOT can list tenants
    mockMvc
        .perform(get("/api/platform/tenants").header("Authorization", "Bearer " + rootToken))
        .andExpect(status().isOk());
  }

  @Test
  void testTenantAdminFlow() throws Exception {
    // 1. Signup Tenant 1
    SignupRequest t1Signup = createSignupRequest("Tenant 1", "t1", "admin-mgt1@t1.com");
    signupService.signup(t1Signup);
    String t1Token = login("admin-mgt1@t1.com", "password123");

    // 2. Tenant ADMIN can create users in their tenant
    CreatePlatformUserRequest createUser = new CreatePlatformUserRequest();
    createUser.setName("Staff User");
    createUser.setEmail("staff-mgt1@t1.com");
    createUser.setPassword("staff123");
    createUser.setRole("STAFF");

    mockMvc
        .perform(
            post("/api/tenant/users")
                .header("Authorization", "Bearer " + t1Token)
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(createUser))))
        .andExpect(status().isCreated());

    // 3. Verify Staff isolation (STAFF cannot access management)
    String staffToken = login("staff-mgt1@t1.com", "staff123");
    mockMvc
        .perform(get("/api/tenant/users").header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void testIsolationBetweenTenants() throws Exception {
    // 1. Signup Tenant 1
    signupService.signup(createSignupRequest("Tenant 1-Iso", "t1-iso", "admin-iso1@t1.com"));
    String t1Token = login("admin-iso1@t1.com", "password123");

    // 2. Signup Tenant 2
    signupService.signup(createSignupRequest("Tenant 2-Iso", "t2-iso", "admin-iso2@t2.com"));
    login("admin-iso2@t2.com", "password123");

    // 3. Tenant 1 Admin cannot see Tenant 2 Admin (Users should be isolated)
    mockMvc
        .perform(get("/api/tenant/users").header("Authorization", "Bearer " + t1Token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].email").value("admin-iso1@t1.com"));
  }

  @Test
  void testRBACEnforcement() throws Exception {
    // 1. Signup Tenant 1
    signupService.signup(createSignupRequest("Tenant 1-RBAC", "t1-rbac", "admin-rbac1@t1.com"));
    String t1Token = login("admin-rbac1@t1.com", "password123");

    // 2. Tenant ADMIN cannot access Platform APIs (ROOT only)
    mockMvc
        .perform(get("/api/platform/tenants").header("Authorization", "Bearer " + t1Token))
        .andExpect(status().isForbidden());
  }

  private SignupRequest createSignupRequest(String name, String workspaceSlug, String email) {
    SignupRequest req = new SignupRequest();
    req.setBusinessName(name);
    req.setBusinessType("Retail");
    req.setWorkspaceSlug(workspaceSlug);
    req.setOwnerName("Owner " + name);
    req.setOwnerEmail(email);
    req.setPassword("password123");
    return req;
  }

  private String login(String email, String password) throws Exception {
    LoginRequest loginRequest = new LoginRequest();
    loginRequest.setEmail(email);
    loginRequest.setPassword(password);
    
    MvcResult result =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(Objects.requireNonNull(objectMapper.writeValueAsString(loginRequest))))
            .andExpect(status().isOk())
            .andReturn();

    String responseJson = result.getResponse().getContentAsString();
    LoginResponse response = objectMapper.readValue(responseJson, LoginResponse.class);
    return response.getAccessToken();
  }
}
