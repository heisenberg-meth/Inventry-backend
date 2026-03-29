package com.ims.tenant.service;

import com.ims.dto.request.UpdateTenantSettingsRequest;
import com.ims.dto.response.TenantResponse;
import com.ims.model.Tenant;
import com.ims.platform.repository.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSettingsService {

  private final TenantRepository tenantRepository;

  @Transactional(readOnly = true)
  public TenantResponse getSettings(Long tenantId) {
    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
    return toResponse(tenant);
  }

  @Transactional
  @CacheEvict(value = "tenant", key = "#tenantId")
  public TenantResponse updateSettings(Long tenantId, UpdateTenantSettingsRequest request) {
    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

    if (request.getDomain() != null && !request.getDomain().equals(tenant.getDomain())) {
      if (tenantRepository.existsByDomain(request.getDomain())) {
        throw new IllegalArgumentException("Domain already in use: " + request.getDomain());
      }
      tenant.setDomain(request.getDomain());
    }

    if (request.getName() != null) {
      tenant.setName(request.getName());
    }

    if (request.getInvoiceSequence() != null) {
      tenant.setInvoiceSequence(request.getInvoiceSequence());
    }

    tenant = tenantRepository.save(tenant);
    log.info("Tenant settings updated for id={}: domain={}", tenantId, tenant.getDomain());
    return toResponse(tenant);
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
}
