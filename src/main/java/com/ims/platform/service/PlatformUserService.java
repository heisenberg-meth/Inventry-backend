package com.ims.platform.service;

import com.ims.dto.CreatePlatformUserRequest;
import com.ims.model.User;
import com.ims.tenant.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformUserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Transactional
  public @NonNull User createPlatformUser(@NonNull CreatePlatformUserRequest request) {
    if (!request.getRole().equals("PLATFORM_ADMIN") && !request.getRole().equals("SUPPORT_ADMIN")) {
      throw new IllegalArgumentException("Invalid role. Must be PLATFORM_ADMIN or SUPPORT_ADMIN.");
    }
    if (userRepository.existsByEmail(request.getEmail())) {
      throw new IllegalArgumentException("Email already in use");
    }

    User user =
        User.builder()
            .name(request.getName())
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .role(request.getRole())
            .scope("PLATFORM")
            .tenantId(null)
            .isActive(true)
            .build();

    return Objects.requireNonNull(userRepository.save(Objects.requireNonNull(user)));
  }

  @Transactional(readOnly = true)
  public @NonNull Page<User> getPlatformUsers(@NonNull Pageable pageable) {
    return Objects.requireNonNull(userRepository.findByTenantIdIsNull(pageable));
  }

  @Transactional
  public @NonNull User updatePlatformUserRole(@NonNull Long id, @NonNull String role) {
    if (!role.equals("PLATFORM_ADMIN") && !role.equals("SUPPORT_ADMIN")) {
      throw new IllegalArgumentException("Invalid role. Must be PLATFORM_ADMIN or SUPPORT_ADMIN.");
    }
    User user =
        userRepository
            .findByIdAndTenantIdIsNull(id)
            .orElseThrow(() -> new EntityNotFoundException("Platform user not found"));

    if (user.getRole().equals("ROOT")) {
      throw new IllegalArgumentException("Cannot modify ROOT user role");
    }

    user.setRole(role);
    return Objects.requireNonNull(userRepository.save(user));
  }

  @Transactional
  public void deactivatePlatformUser(@NonNull Long id) {
    User user =
        userRepository
            .findByIdAndTenantIdIsNull(id)
            .orElseThrow(() -> new EntityNotFoundException("Platform user not found"));

    if (user.getRole().equals("ROOT")) {
      throw new IllegalArgumentException("Cannot deactivate ROOT user");
    }

    user.setIsActive(false);
    userRepository.save(user);
  }
}
