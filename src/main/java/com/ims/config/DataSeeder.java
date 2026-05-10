package com.ims.config;

import com.ims.model.Tenant;
import com.ims.model.User;
import com.ims.platform.repository.TenantRepository;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@org.springframework.context.annotation.Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

  private final UserRepository userRepository;
  private final TenantRepository tenantRepository;
  private final PasswordEncoder passwordEncoder;

  @Override
  public void run(String... args) {
    try {
      // 1. Create a "System" tenant to satisfy FK and BaseEntity requirements
      Tenant systemTenant = seedSystemTenant();

      // 2. Set TenantContext
      TenantContext.setTenantId(systemTenant.getId());

      // 3. Seed Platform Admin
      seedPlatformAdmin(systemTenant.getId());

    } catch (Exception e) {
      log.error("Failed to seed startup data: {}", e.getMessage(), e);
    } finally {
      TenantContext.clear();
    }
  }

  private Tenant seedSystemTenant() {
    String slug = "system";
    return tenantRepository
        .findByWorkspaceSlug(slug)
        .orElseGet(
            () -> {
              Tenant tenant =
                  Tenant.builder()
                      .name("System Tenant")
                      .workspaceSlug(slug)
                      .companyCode("SYSTEM")
                      .businessType("SYSTEM")
                      .status("ACTIVE")
                      .isActive(true)
                      .build();
              Tenant saved = tenantRepository.save(tenant);
              log.info("System Tenant seeded with ID: {}", saved.getId());
              return saved;
            });
  }

  private void seedPlatformAdmin(Long tenantId) {
    String adminEmail = "admin@platform.com";
    if (userRepository.findByEmailUnfiltered(adminEmail).isEmpty()) {
      User admin =
          User.builder()
              .tenantId(tenantId)
              .name("Platform Admin")
              .email(adminEmail)
              .passwordHash(passwordEncoder.encode("admin123"))
              .role("PLATFORM_ADMIN")
              .scope("PLATFORM")
              .isPlatformUser(true)
              .isActive(true)
              .isVerified(true)
              .build();
      userRepository.save(admin);
      log.info("Platform Admin seeded: {}", adminEmail);
    }
  }
}
