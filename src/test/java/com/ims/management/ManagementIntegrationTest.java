package com.ims.management;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.ims.BaseIntegrationTest;
import com.ims.dto.request.CreateUserRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.SignupResponse;
import com.ims.shared.auth.SignupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import java.util.Objects;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration",
    "spring.cache.type=none"
})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test-no-security")
public class ManagementIntegrationTest extends BaseIntegrationTest {

  @Autowired
  private SignupService signupService;

  @BeforeEach
  void setup() {
    cleanupDatabase();
    mockRedisAndCache();
  }

  @Test
  void testPlatformAdminFlow() throws Exception {
    if (TEST_ROOT_PASSWORD == null || TEST_ROOT_PASSWORD.isBlank()) {
      throw new IllegalStateException("TEST_ROOT_PASSWORD must be configured");
    }

    final String test_ROOT_PASSWORD2 = TEST_ROOT_PASSWORD;
    if (test_ROOT_PASSWORD2 != null) {
      String rootToken = login("root@ims.com", test_ROOT_PASSWORD2, "SYS001", systemTenantId);
      // ROOT can list tenants
      mockMvc
          .perform(
              get("/api/v1/platform/tenants")
                  .header("Authorization", "Bearer " + rootToken)
                  .with(tenant(systemTenantId)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content").isArray());
    }
  }

  @Test
  void testTenantAdminFlow() throws Exception {
    // 1. Signup Tenant 1
    SignupRequest t1Signup = createSignupRequest("Tenant 1", "t1-mgt", "admin-mgt1@t1.com");
    SignupResponse response = signupService.signup(Objects.requireNonNull(t1Signup));
    verifyUserEmail("admin-mgt1@t1.com");
    verifyUser("admin-mgt1@t1.com");

    Long t1Id = Objects.requireNonNull(tenantRepository.findByWorkspaceSlug("t1-mgt").orElseThrow().getId());
    String t1Token = login("admin-mgt1@t1.com", "password123", Objects.requireNonNull(response.getCompanyCode()), t1Id);

    // 2. Tenant ADMIN can create users in their tenant
    CreateUserRequest createUser = new CreateUserRequest();
    createUser.setName("Staff User");
    createUser.setEmail("staff-mgt1@t1.com");
    createUser.setPassword("staff123");
    createUser.setRole("STAFF");

    String createUserJson = objectMapper.writeValueAsString(createUser);
    mockMvc
        .perform(
            post("/api/v1/tenant/users")
                .header("Authorization", "Bearer " + t1Token)
                .with(tenant(Objects.requireNonNull(t1Id.toString())))
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(createUserJson)))
        .andExpect(status().isCreated());

    // 3. Verify Staff isolation (STAFF cannot access management)
    verifyUserEmail("staff-mgt1@t1.com");
    String staffToken = login("staff-mgt1@t1.com", "staff123", Objects.requireNonNull(response.getCompanyCode()), t1Id);
    mockMvc
        .perform(
            get("/api/v1/tenant/users")
                .header("Authorization", "Bearer " + staffToken)
                .with(tenant(Objects.requireNonNull(t1Id.toString()))))
        .andExpect(status().isForbidden());
  }

  @Test
  void testIsolationBetweenTenants() throws Exception {
    // 1. Signup Tenant 1
    SignupResponse r1 = signupService
        .signup(Objects.requireNonNull(createSignupRequest("Tenant 1-Iso", "t1-iso", "admin-iso1@t1.com")));
    verifyUserEmail("admin-iso1@t1.com");
    verifyUser("admin-iso1@t1.com");
    Long t1Id = Objects.requireNonNull(tenantRepository.findByWorkspaceSlug("t1-iso").orElseThrow().getId());
    String t1Token = login("admin-iso1@t1.com", "password123", Objects.requireNonNull(r1.getCompanyCode()), t1Id);

    // 2. Signup Tenant 2
    SignupResponse r2 = signupService
        .signup(Objects.requireNonNull(createSignupRequest("Tenant 2-Iso", "t2-iso", "admin-iso2@t2.com")));
    verifyUserEmail("admin-iso2@t2.com");
    verifyUser("admin-iso2@t2.com");
    Long t2Id = Objects.requireNonNull(tenantRepository.findByWorkspaceSlug("t2-iso").orElseThrow().getId());
    login("admin-iso2@t2.com", "password123", Objects.requireNonNull(r2.getCompanyCode()), t2Id);

    // 3. Tenant 1 Admin cannot see Tenant 2 Admin (Users should be isolated)
    mockMvc
        .perform(
            get("/api/v1/tenant/users")
                .header("Authorization", "Bearer " + t1Token)
                .with(tenant(Objects.requireNonNull(t1Id.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].email").value("admin-iso1@t1.com"));
  }

  @Test
  void testRBACEnforcement() throws Exception {
    // 1. Signup Tenant 1
    SignupResponse r1 = signupService
        .signup(Objects.requireNonNull(createSignupRequest("Tenant 1-RBAC", "t1-rbac", "admin-rbac1@t1.com")));
    verifyUserEmail("admin-rbac1@t1.com");
    verifyUser("admin-rbac1@t1.com");
    Long t1Id = Objects.requireNonNull(tenantRepository.findByWorkspaceSlug("t1-rbac").orElseThrow().getId());
    String t1Token = login("admin-rbac1@t1.com", "password123", Objects.requireNonNull(r1.getCompanyCode()), t1Id);

    // 2. Tenant ADMIN cannot access Platform APIs (ROOT only)
    mockMvc
        .perform(
            get("/api/v1/platform/tenants")
                .header("Authorization", "Bearer " + t1Token)
                .with(tenant(Objects.requireNonNull(t1Id.toString()))))
        .andExpect(status().isForbidden());
  }

  private SignupRequest createSignupRequest(String name, String workspaceSlug,
      String email) {
    SignupRequest req = new SignupRequest();
    req.setBusinessName(Objects.requireNonNull(name));
    req.setBusinessType("Retail");
    req.setWorkspaceSlug(Objects.requireNonNull(workspaceSlug));
    req.setOwnerName(Objects.requireNonNull("Owner " + name));
    req.setOwnerEmail(Objects.requireNonNull(email));
    req.setPassword("password123");
    return req;
  }
}
