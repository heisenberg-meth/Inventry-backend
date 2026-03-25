package com.ims.config;

import com.ims.model.User;
import com.ims.tenant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Override
  public void run(ApplicationArguments args) {
    // Seed ROOT user if not exists
    if (!userRepository.existsByEmail("root@ims.com")) {
      User root =
          User.builder()
              .name("Root Admin")
              .email("root@ims.com")
              .passwordHash(passwordEncoder.encode("root123"))
              .role("ROOT")
              .tenantId(null) // Platform-level user, no tenant
              .isActive(true)
              .build();
      userRepository.save(root);
      log.info("Seed data: ROOT user created (root@ims.com / root123)");
    } else {
      log.info("Seed data: ROOT user already exists");
    }
  }
}
