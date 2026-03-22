package com.ims.tenant.service;

import com.ims.dto.request.CreateUserRequest;
import com.ims.dto.response.UserResponse;
import com.ims.model.User;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final List<String> VALID_TENANT_ROLES = List.of("ADMIN", "MANAGER", "STAFF");

    public Page<UserResponse> getUsers(Pageable pageable) {
        Long tenantId = TenantContext.get();
        return userRepository.findByTenantId(tenantId, pageable).map(this::toResponse);
    }

    public UserResponse getUserById(Long id) {
        Long tenantId = TenantContext.get();
        User user = userRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        return toResponse(user);
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        Long tenantId = TenantContext.get();

        // Validate role is tenant-level only
        if (!VALID_TENANT_ROLES.contains(request.getRole())) {
            throw new IllegalArgumentException("Invalid role. Must be one of: " + VALID_TENANT_ROLES);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use: " + request.getEmail());
        }

        User user = User.builder()
                .tenantId(tenantId)
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .isActive(true)
                .build();

        user = userRepository.save(user);
        log.info("User created: id={} email={} role={} tenant={}", user.getId(), user.getEmail(), user.getRole(), tenantId);
        return toResponse(user);
    }

    @Transactional
    public UserResponse updateRole(Long id, String newRole) {
        Long tenantId = TenantContext.get();

        if (!VALID_TENANT_ROLES.contains(newRole)) {
            throw new IllegalArgumentException("Invalid role. Must be one of: " + VALID_TENANT_ROLES);
        }

        User user = userRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        user.setRole(newRole);
        user = userRepository.save(user);
        log.info("User role updated: id={} newRole={}", id, newRole);
        return toResponse(user);
    }

    @Transactional
    public void deactivateUser(Long id) {
        Long tenantId = TenantContext.get();
        User user = userRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        user.setIsActive(false);
        userRepository.save(user);
        log.info("User deactivated: id={}", id);
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
