package com.ims.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.ims.BaseIntegrationTest;
import com.ims.dto.request.SignupRequest;
import com.ims.shared.auth.SignupService;
import com.ims.shared.auth.TenantContext;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

public class AuthIntegrationTest extends BaseIntegrationTest {

  @Autowired
  private SignupService signupService;

  @Override
  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    cleanupDatabase();
  }

  @Test
  void testSignupAndTenantIsolation() throws Exception {
    String uniqueId1 = UUID.randomUUID().toString().substring(0, 8);
    SignupRequest t1Signup = createSignupRequest(
        "Tenant 1 " + uniqueId1, "t1-auth-" + uniqueId1, "admin1-" + uniqueId1 + "@t1.com");
    signupService.signup(t1Signup);

    String uniqueId2 = UUID.randomUUID().toString().substring(0, 8);
    SignupRequest t2Signup = createSignupRequest(
        "Tenant 2 " + uniqueId2, "t2-auth-" + uniqueId2, "admin2-" + uniqueId2 + "@t2.com");
    signupService.signup(t2Signup);

    verifyUserEmail("admin1-" + uniqueId1 + "@t1.com");
    verifyUserEmail("admin2-" + uniqueId2 + "@t2.com");

    Long t1Id = Objects.requireNonNull(
        tenantRepository.findByWorkspaceSlug("t1-auth-" + uniqueId1).orElseThrow().getId());
    Long t2Id = Objects.requireNonNull(
        tenantRepository.findByWorkspaceSlug("t2-auth-" + uniqueId2).orElseThrow().getId());

    TenantContext.setTenantId(t1Id);
    var t1Users = userRepository.findAll();
    TenantContext.clear();

    TenantContext.setTenantId(t2Id);
    var t2Users = userRepository.findAll();
    TenantContext.clear();

    Assertions.assertEquals(1, t1Users.size());
    Assertions.assertEquals(1, t2Users.size());
    Assertions.assertTrue(t1Users.get(0).getEmail().contains("@t1.com"));
    Assertions.assertTrue(t2Users.get(0).getEmail().contains("@t2.com"));
  }

  @Test
  void testUnauthorizedAccess() throws Exception {
    mockMvc.perform(get("/api/v1/tenant/users").header("X-Tenant-ID", "1"))
        .andExpect(status().isUnauthorized());
  }

  private SignupRequest createSignupRequest(String name, String workspaceSlug, String email) {
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