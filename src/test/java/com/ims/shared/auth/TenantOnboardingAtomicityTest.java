package com.ims.shared.auth;

import static org.assertj.core.api.Assertions.assertThat;
import com.ims.BaseIntegrationTest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.SignupResponse;
import com.ims.model.Tenant;
import com.ims.platform.repository.SubscriptionRepository;
import com.ims.platform.repository.TenantRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class TenantOnboardingAtomicityTest extends BaseIntegrationTest {

  @Autowired
  private SignupService signupService;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private SubscriptionRepository subscriptionRepository;

  @Override
  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    TenantContext.clear();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void testSignupSuccess_createsAllRequiredEntities() {
    long initialTenantCount = tenantRepository.count();

    SignupRequest request = new SignupRequest();
    request.setBusinessName("Atomicity Test Inc " + UUID.randomUUID());
    request.setBusinessType("RETAIL");
    request.setOwnerName("John Doe");
    request.setOwnerEmail("atomicity" + UUID.randomUUID() + "@test.com");
    request.setPassword("SecurePass123!");
    request.setIdempotencyKey(UUID.randomUUID().toString());

    SignupResponse response = signupService.signup(request);

    assertThat(response).isNotNull();
    assertThat(response.getWorkspaceSlug()).isNotBlank();

    // Verify entities were created
    assertThat(tenantRepository.count()).isEqualTo(initialTenantCount + 1);

    Tenant createdTenant = tenantRepository.findByWorkspaceSlug(response.getWorkspaceSlug()).orElseThrow();

    // Check trial subscription
    assertThat(
        subscriptionRepository.findByTenantIdAndStatus(createdTenant.getId(), com.ims.model.SubscriptionStatus.TRIAL))
        .isNotEmpty();

    // Check settings
    assertThat(createdTenant.getInvoiceSequence()).isEqualTo(1000);
    assertThat(createdTenant.getCurrency()).isEqualTo("INR");
  }

  @Test
  void testSignupIdempotency_sameKey_returnsExisting() {
    String idempotencyKey = UUID.randomUUID().toString();

    // First signup
    SignupRequest request1 = new SignupRequest();
    request1.setBusinessName("Idem Test Inc");
    request1.setBusinessType("RETAIL");
    request1.setOwnerName("Idem Owner");
    request1.setOwnerEmail("idem" + UUID.randomUUID() + "@test.com");
    request1.setPassword("SecurePass123!");
    request1.setIdempotencyKey(idempotencyKey);

    SignupResponse response1 = signupService.signup(request1);
    long tenantCount = tenantRepository.count();

    // Second signup with same key - should return existing
    SignupRequest request2 = new SignupRequest();
    request2.setBusinessName("Idem Test Inc");
    request2.setBusinessType("RETAIL");
    request2.setOwnerName("Idem Owner");
    request2.setOwnerEmail("idem2" + UUID.randomUUID() + "@test.com");
    request2.setPassword("SecurePass123!");
    request2.setIdempotencyKey(idempotencyKey);

    SignupResponse response2 = signupService.signup(request2);

    // Should return same tenant, no new tenant created
    assertThat(response2.getWorkspaceSlug()).isEqualTo(response1.getWorkspaceSlug());
    assertThat(tenantRepository.count()).isEqualTo(tenantCount);
  }
}