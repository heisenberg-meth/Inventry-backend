package com.ims.config;

import com.ims.model.User;
import com.ims.model.UserRole;
import com.ims.tenant.repository.UserRepository;
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

  @Value("${app.seeder.admin-password:#{null}}")
  private String seedAdminPassword;

  @Override
  public void run(String... args) {
    seedPlatformAdmin();
  }

  private void seedPlatformAdmin() {
    if (seedAdminPassword == null || seedAdminPassword.isBlank()) {
      log.warn("Seeder skipped: app.seeder.admin-password not set");
      return;
    }

    String adminEmail = "admin@platform.com";
    if (userRepository.findByEmailUnfiltered(adminEmail).isEmpty()) {
      User admin =
          User.builder()
              .name("Platform Admin")
              .email(adminEmail)
              .passwordHash(passwordEncoder.encode(seedAdminPassword))
              .role(UserRole.PLATFORM_ADMIN)
              .scope("PLATFORM")
              .isPlatformUser(true)
              .isActive(true)
              .build();
      userRepository.save(admin);
      log.info("Platform Admin seeded: {}", adminEmail);
    }
  }
}
