package com.ims.platform.service;

import com.ims.dto.request.CreateTenantRequest;
import com.ims.dto.response.TenantResponse;
import com.ims.model.Tenant;
import com.ims.platform.repository.TenantRepository;
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
        .createdAt(tenant.getCreatedAt())
        .build();
  }
}
