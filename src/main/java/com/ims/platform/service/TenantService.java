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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {

  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final UserCreationService userCreationService;

  public Page<TenantResponse> getAllTenants(Pageable pageable) {
    return tenantRepository.findAll(pageable).map(this::toResponse);
  }

  @Cacheable(value = "tenant", key = "#id")
  public TenantResponse getTenantById(Long id) {
    Tenant tenant =
        tenantRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + id));
    return toResponse(tenant);
  }

  @Transactional
  public TenantResponse createTenant(CreateTenantRequest request) {
    if (request.getDomain() != null && tenantRepository.existsByDomain(request.getDomain())) {
      throw new IllegalArgumentException("Domain already taken: " + request.getDomain());
    }

    Tenant tenant =
        Tenant.builder()
            .name(request.getName())
            .domain(request.getDomain())
            .businessType(request.getBusinessType())
            .plan(request.getPlan() != null ? request.getPlan() : "FREE")
            .status("ACTIVE")
            .maxProducts(request.getMaxProducts())
            .maxUsers(request.getMaxUsers())
            .build();

    tenant = tenantRepository.save(tenant);
    log.info(
        "Tenant created: id={} name={} type={}",
        tenant.getId(),
        tenant.getName(),
        tenant.getBusinessType());
    return toResponse(tenant);
  }

  @Transactional
  @CacheEvict(value = "tenant", key = "#id")
  public TenantResponse updateTenant(Long id, CreateTenantRequest request) {
    Tenant tenant =
        tenantRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + id));

    if (request.getName() != null) {
      tenant.setName(request.getName());
    }
    if (request.getPlan() != null) {
      tenant.setPlan(request.getPlan());
    }
    if (request.getBusinessType() != null) {
      tenant.setBusinessType(request.getBusinessType());
    }
    if (request.getMaxProducts() != null) {
      tenant.setMaxProducts(request.getMaxProducts());
    }
    if (request.getMaxUsers() != null) {
      tenant.setMaxUsers(request.getMaxUsers());
    }

    tenant = tenantRepository.save(tenant);
    return toResponse(tenant);
  }

  @Transactional
  @CacheEvict(value = "tenant", key = "#id")
  public void deactivateTenant(Long id) {
    Tenant tenant =
        tenantRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + id));
    tenant.setStatus("INACTIVE");
    tenantRepository.save(tenant);
    log.info("Tenant deactivated: id={}", id);
  }

  private TenantResponse toResponse(Tenant tenant) {
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
  public UserResponse createTenantUser(Long tenantId, CreateTenantUserRequest request) {
    if (!tenantRepository.existsById(tenantId)) {
      throw new EntityNotFoundException("Tenant not found with id: " + tenantId);
    }
    if (userRepository.existsByEmail(request.getEmail())) {
      throw new IllegalArgumentException("Email already in use");
    }

    User user =
        User.builder()
            .name(request.getUsername())
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .role(request.getRole())
            .scope(request.getScope())
            .tenantId(tenantId)
            .isActive(true)
            .build();

    var tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
    if (tenant.getMaxUsers() != null) {
      long currentCount = userRepository.countActiveByTenantId(tenantId);
      if (currentCount >= tenant.getMaxUsers()) {
        throw new IllegalArgumentException(
            "User limit reached for this tenant (" + tenant.getMaxUsers() + ")");
      }
    }

    userCreationService.createUserForTenant(user, tenantId);

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
