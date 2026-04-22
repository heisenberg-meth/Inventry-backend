package com.ims.config;

import com.ims.model.User;
import com.ims.model.UserRole;
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
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedPlatformAdmin();
    }

    private void seedPlatformAdmin() {
        String adminEmail = "admin@platform.com";
        if (userRepository.findByEmailUnfiltered(adminEmail).isEmpty()) {
            User admin = User.builder()
                    .name("Platform Admin")
                    .email(adminEmail)
                    .passwordHash(passwordEncoder.encode("admin-Secret-1234!"))
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
