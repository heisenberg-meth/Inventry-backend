package com.ims.helper;

import com.ims.model.Tenant;
import com.ims.model.User;
import com.ims.platform.repository.TenantRepository;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TestDataFactory {

  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public Tenant createTenant() {
    Tenant tenant = new Tenant();
    tenant.setName("Test Tenant " + UUID.randomUUID().toString().substring(0, 8));
    tenant.setWorkspaceSlug("slug-" + UUID.randomUUID().toString().substring(0, 8));
    tenant.setCompanyCode(UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    tenant.setStatus("ACTIVE");
    tenant.setIsActive(true);
    tenant.setBusinessType("RETAIL");
    return tenantRepository.save(tenant);
  }

  public User createUser(Tenant tenant) {
    TenantContext.setTenantId(tenant.getId());

    User user = new User();
    user.setName("Test User");
    user.setEmail("user-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com");
    user.setPasswordHash(passwordEncoder.encode("password123"));
    user.setRole("ADMIN");
    user.setScope("TENANT");
    user.setTenantId(tenant.getId());
    user.setIsActive(true);
    user.setIsVerified(true);
    user.setIsPlatformUser(false);

    return userRepository.save(user);
  }

  public User createPlatformUser(String email, String password, String role) {
    User user = new User();
    user.setName("Platform Admin");
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setRole(role);
    user.setScope("PLATFORM");
    user.setTenantId(null);
    user.setIsActive(true);
    user.setIsVerified(true);
    user.setIsPlatformUser(true);

    return userRepository.save(user);
  }

  public void initializeTenantContext(Tenant tenant) {
    TenantContext.setTenantId(tenant.getId());
  }

  // Static helper methods from original TestDataFactory
  public static String email() {
    return "user_" + UUID.randomUUID() + "@test.com";
  }

  public static String business() {
    return "biz_" + UUID.randomUUID();
  }

  public static String slug() {
    return "slug_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
  }

  public static String sku() {
    return "SKU-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
  }

  public static String companyCode() {
    return "CC" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
  }
}
