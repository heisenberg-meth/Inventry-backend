package com.ims.config;

import com.ims.model.User;
import com.ims.tenant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

//@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Override
  public void run(ApplicationArguments args) {
    // Check if root already exists anywhere (to avoid duplicates due to multi-tenancy filters)
    boolean exists = userRepository.findByEmail("root@ims.com").isPresent();

    if (!exists) {
      log.info("Seed data: ROOT user not found. Creating...");
      User root =
          User.builder()
              .name("Root Admin")
              .email("root@ims.com")
              .passwordHash(passwordEncoder.encode("root123"))
              .role("ROOT")
              .scope("PLATFORM")
              .tenantId(0L) // Assigned to system tenant 0
              .isActive(true)
              .build();
      userRepository.save(root);
      log.info("Seed data: ROOT user created (root@ims.com / root123)");
    } else {
      log.info("Seed data: ROOT user already exists");
    }
  }
}
