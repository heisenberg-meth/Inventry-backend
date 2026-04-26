package com.ims.config;

import com.ims.model.Role;
import com.ims.model.User;
import com.ims.model.UserRole;
import com.ims.tenant.repository.RoleRepository;
import com.ims.tenant.repository.UserRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@org.springframework.context.annotation.Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;

  @Value("${PLATFORM_ADMIN_PASSWORD:#{null}}")
  private String platformAdminPassword;

  @Value("${seed.admin:false}")
  private boolean seedAdmin;

  @Value("${spring.profiles.active:}")
  private String activeProfile;

  @jakarta.annotation.PostConstruct
  public void validateProfile() {
    if (activeProfile == null || activeProfile.isBlank()) {
      log.warn("SPRING_PROFILES_ACTIVE is not set. Defaulting to safe behavior.");
      return;
    }

    if (!activeProfile.contains("dev") && !activeProfile.contains("test")) {
      throw new IllegalStateException("DataSeeder is active but the profile is NOT dev/test. Security breach prevention triggered.");
    }
  }

  @Override
  public void run(String... args) {
    if (!seedAdmin) {
      return;
    }
    seedPlatformAdmin();
  }

  private void seedPlatformAdmin() {
    if (platformAdminPassword == null || platformAdminPassword.isBlank()) {
      throw new IllegalStateException("PLATFORM_ADMIN_PASSWORD env var is required when seed.admin=true");
    }

    // 1. Ensure the Platform Admin role exists
    Role platformAdminRole = roleRepository.findByNameAndTenantIdIsNull(UserRole.PLATFORM_ADMIN.name())
        .orElseGet(() -> {
          Role role = Role.builder()
              .name(Objects.requireNonNull(UserRole.PLATFORM_ADMIN.name()))
              .description("Global Platform Administrator")
              .build();
          return roleRepository.save(role);
        });

    String adminEmail = "admin@platform.com";
    if (userRepository.findByEmailGlobal(adminEmail).isEmpty()) {
      User admin =
          Objects.requireNonNull(
              User.builder()
                  .name("Platform Admin")
                  .email(adminEmail)
                  .passwordHash(Objects.requireNonNull(passwordEncoder.encode(platformAdminPassword)))
                  .role(Objects.requireNonNull(platformAdminRole))
                  .scope("PLATFORM")
                  .isPlatformUser(true)
                  .isActive(true)
                  .build());
      userRepository.save(admin);
      log.info("Platform Admin seeded: {}", adminEmail);
    }
  }
}
