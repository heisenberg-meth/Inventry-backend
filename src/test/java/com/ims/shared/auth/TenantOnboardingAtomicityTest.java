package com.ims.shared.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.SignupResponse;
import com.ims.model.Tenant;
import com.ims.platform.repository.SubscriptionRepository;
import com.ims.platform.repository.TenantRepository;
import com.ims.tenant.repository.UserRepository;
import com.ims.tenant.service.TenantSettingsService;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public class TenantOnboardingAtomicityTest {

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
      .withDatabaseName("inventory_test")
      .withUsername("test")
      .withPassword("test");

  @DynamicPropertySource
  static void registerPgProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired
  private SignupService signupService;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private SubscriptionRepository subscriptionRepository;

  @MockitoSpyBean
  private TenantSettingsService tenantSettingsService;

  @MockitoSpyBean
  private TenantInitializationService tenantInitializationService;

  @BeforeEach
  void setUp() {
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
    assertThat(subscriptionRepository.findByTenantIdAndStatus(createdTenant.getId(), com.ims.model.SubscriptionStatus.TRIAL)).isNotEmpty();
    
    // Check settings
    assertThat(createdTenant.getInvoiceSequence()).isEqualTo(1000);
    assertThat(createdTenant.getCurrency()).isEqualTo("INR");
  }

  @Test
  void testSignupAtomicity_whenSettingsInitializationFails_thenTenantIsRolledBack() {
    long initialTenantCount = tenantRepository.count();
    long initialUserCount = userRepository.count();

    // Force an exception during the final step of the signup flow
    doThrow(new RuntimeException("Simulated Settings Failure"))
        .when(tenantSettingsService).initializeDefaults(any());

    SignupRequest request = new SignupRequest();
    request.setBusinessName("Rollback Settings Inc " + UUID.randomUUID());
    request.setBusinessType("RETAIL");
    request.setOwnerName("Jane Settings");
    request.setOwnerEmail("rollback.settings" + UUID.randomUUID() + "@test.com");
    request.setPassword("SecurePass123!");
    request.setIdempotencyKey(UUID.randomUUID().toString());

    assertThatThrownBy(() -> signupService.signup(request))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Simulated Settings Failure");

    // The core validation: Even though tenantRepository.saveAndFlush() was called,
    // the transaction boundary should have rolled it back entirely.
    assertThat(tenantRepository.count())
        .as("Tenant should not exist after failed signup transaction")
        .isEqualTo(initialTenantCount);

    assertThat(userRepository.count())
        .as("User should not exist after failed signup transaction")
        .isEqualTo(initialUserCount);
  }

  @Test
  void testSignupAtomicity_whenRoleSeedingFails_thenTenantIsRolledBack() {
    long initialTenantCount = tenantRepository.count();

    // Force an exception during role seeding (which happens right after user creation)
    doThrow(new RuntimeException("Simulated Role Failure"))
        .when(tenantInitializationService).initializeTenant(any(), any(), any());

    SignupRequest request = new SignupRequest();
    request.setBusinessName("Rollback Role Inc " + UUID.randomUUID());
    request.setBusinessType("RETAIL");
    request.setOwnerName("Jane Role");
    request.setOwnerEmail("rollback.role" + UUID.randomUUID() + "@test.com");
    request.setPassword("SecurePass123!");
    request.setIdempotencyKey(UUID.randomUUID().toString());

    assertThatThrownBy(() -> signupService.signup(request))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Simulated Role Failure");

    // Verify rollback
    assertThat(tenantRepository.count())
        .as("Tenant should not exist after failed signup transaction")
        .isEqualTo(initialTenantCount);
  }
}
