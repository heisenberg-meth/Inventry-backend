package com.ims.shared.auth;

import static org.junit.jupiter.api.Assertions.*;
import com.ims.BaseIntegrationTest;
import com.ims.dto.request.SignupRequest;
import com.ims.model.Tenant;
import com.ims.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Optional;

public class SignupIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private SignupService signupService;

    @Override
    @BeforeEach
    protected void setUp() throws Exception {
        super.setUp();
        cleanupDatabase();
    }

    @Test
    void testSuccessfulSignup() throws Exception {
        SignupRequest request = createValidSignupRequest("SuccessBiz", "success-biz");
        signupService.signup(request);

        Optional<Tenant> tenant = tenantRepository.findByWorkspaceSlug("success-biz");
        assertTrue(tenant.isPresent());

        // Verify user was created in the tenant
        TenantContext.setTenantId(tenant.get().getId());
        Optional<User> user = userRepository.findByEmail(request.getOwnerEmail().toLowerCase());
        assertTrue(user.isPresent());
        TenantContext.clear();
    }

    @Test
    void testSignupAtomicityOnUserConflict() throws Exception {
        String email = "conflict@test.com";
        // Create an existing user first
        signupService.signup(createValidSignupRequest("FirstBiz", "first-biz", email));

        // Attempt another signup with the same email
        SignupRequest secondRequest = createValidSignupRequest("SecondBiz", "second-biz", email);

        try {
            signupService.signup(secondRequest);
            fail("Should have thrown ConflictException");
        } catch (Exception e) {
            // Expected
        }

        // Verify second tenant was NOT created
        Optional<Tenant> tenant = tenantRepository.findByWorkspaceSlug("second-biz");
        assertFalse(tenant.isPresent(), "Second tenant should have been rolled back due to user conflict");
    }

    private SignupRequest createValidSignupRequest(String bizName, String slug) {
        return createValidSignupRequest(bizName, slug, bizName.toLowerCase() + "@test.com");
    }

    private SignupRequest createValidSignupRequest(String bizName, String slug, String email) {
        SignupRequest req = new SignupRequest();
        req.setBusinessName(bizName);
        req.setBusinessType("Retail");
        req.setWorkspaceSlug(slug);
        req.setOwnerName("Owner " + bizName);
        req.setOwnerEmail(email);
        req.setPassword("password123");
        return req;
    }
}
