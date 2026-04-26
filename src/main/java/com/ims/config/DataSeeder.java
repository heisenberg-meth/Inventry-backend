package com.ims.config;

import com.ims.model.User;
import com.ims.model.UserRole;
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

    String adminEmail = "admin@platform.com";
    if (userRepository.findByEmailUnfiltered(adminEmail).isEmpty()) {
      User admin =
          Objects.requireNonNull(
              User.builder()
                  .name("Platform Admin")
                  .email(adminEmail)
                  .passwordHash(passwordEncoder.encode(platformAdminPassword))
                  .role(UserRole.PLATFORM_ADMIN)
                  .scope("PLATFORM")
                  .isPlatformUser(true)
                  .isActive(true)
                  .build());
      userRepository.save(admin);
      log.info("Platform Admin seeded: {}", adminEmail);
    }
  }
}
