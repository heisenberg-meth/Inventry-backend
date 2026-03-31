package com.ims.platform.service;

import com.ims.dto.request.CreateTenantRequest;
import com.ims.dto.response.TenantResponse;
import com.ims.model.Tenant;
import com.ims.platform.repository.TenantRepository;
import com.ims.dto.request.CreateTenantUserRequest;
import com.ims.dto.response.UserResponse;
import com.ims.model.User;
import com.ims.tenant.repository.UserRepository;
import com.ims.shared.auth.UserCreationService;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.lang.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {

  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final UserCreationService userCreationService;

  public Page<TenantResponse> getAllTenants(@NonNull Pageable pageable) {
    Page<Tenant> tenants = Objects.requireNonNull(tenantRepository.findAll(pageable));
    return tenants.map(this::toResponse);
  }

  @Cacheable(value = "tenant", key = "#id")
  public TenantResponse getTenantById(@NonNull Long id) {
    Tenant tenant = tenantRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + id));
    return toResponse(Objects.requireNonNull(tenant));
  }

  @Transactional
  public TenantResponse createTenant(@NonNull CreateTenantRequest request) {
    if (request.getDomain() != null && tenantRepository.existsByDomain(request.getDomain())) {
      throw new IllegalArgumentException("Domain already taken: " + request.getDomain());
    }

    Tenant tenant = Tenant.builder()
            .name(request.getName())
            .domain(request.getDomain())
            .businessType(request.getBusinessType())
            .plan(request.getPlan() != null ? request.getPlan() : "FREE")
            .status("ACTIVE")
            .maxProducts(request.getMaxProducts())
            .maxUsers(request.getMaxUsers())
            .build();

    Tenant savedTenant = tenantRepository.save(Objects.requireNonNull(tenant));
    
    log.info("Tenant created: id={} name={} type={}",
        savedTenant.getId(),
        savedTenant.getName(),
        savedTenant.getBusinessType());
    return toResponse(savedTenant);
  }

  @Transactional
  @CacheEvict(value = "tenant", key = "#id")
  public TenantResponse updateTenant(@NonNull Long id, @NonNull CreateTenantRequest request) {
    Tenant tenant = tenantRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + id));

    if (request.getName() != null) tenant.setName(request.getName());
    if (request.getPlan() != null) tenant.setPlan(request.getPlan());
    if (request.getBusinessType() != null) tenant.setBusinessType(request.getBusinessType());
    if (request.getMaxProducts() != null) tenant.setMaxProducts(request.getMaxProducts());
    if (request.getMaxUsers() != null) tenant.setMaxUsers(request.getMaxUsers());

    Tenant updatedTenant = tenantRepository.save(Objects.requireNonNull(tenant));
    return toResponse(updatedTenant);
  }

  @Transactional
  @CacheEvict(value = "tenant", key = "#id")
  public void deactivateTenant(@NonNull Long id) {
    Tenant tenant = tenantRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + id));
    tenant.setStatus("INACTIVE");
    tenantRepository.save(tenant);
    log.info("Tenant deactivated: id={}", id);
  }

  private TenantResponse toResponse(@NonNull Tenant tenant) {
    return TenantResponse.builder()
        .id(tenant.getId())
        .name(tenant.getName())
        .domain(tenant.getDomain())
        .businessType(tenant.getBusinessType())
        .plan(tenant.getPlan())
        .status(tenant.getStatus())
        .maxProducts(tenant.getMaxProducts())
        .maxUsers(tenant.getMaxUsers())
        .createdAt(tenant.getCreatedAt())
        .build();
  }

  @Transactional
  public UserResponse createTenantUser(@NonNull Long tenantId, @NonNull CreateTenantUserRequest request) {
    if (!tenantRepository.existsById(tenantId)) {
      throw new EntityNotFoundException("Tenant not found with id: " + tenantId);
    }
    
    // Explicitly check the email to avoid passing a potential null into userRepository
    String email = Objects.requireNonNull(request.getEmail(), "Email cannot be null");
    if (userRepository.existsByEmail(email)) {
      throw new IllegalArgumentException("Email already in use");
    }

    User user = User.builder()
            .name(request.getUsername())
            .email(email)
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .role(request.getRole())
            .scope(request.getScope())
            .tenantId(tenantId)
            .isActive(true)
            .build();

    Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

    if (tenant.getMaxUsers() != null) {
      long currentCount = userRepository.countActiveByTenantId(tenantId);
      if (currentCount >= tenant.getMaxUsers()) {
        throw new IllegalArgumentException("User limit reached (" + tenant.getMaxUsers() + ")");
      }
    }

    userCreationService.createUserForTenant(Objects.requireNonNull(user), tenantId);

    return UserResponse.builder()
        .id(user.getId())
        .name(user.getName())
        .email(user.getEmail())
        .role(user.getRole())
        .scope(user.getScope())
        .isActive(user.getIsActive())
        .createdAt(user.getCreatedAt())
        .build();
  }
}