package com.ims.tenant.service;

import com.ims.dto.request.UpdateTenantSettingsRequest;
import com.ims.dto.response.TenantResponse;
import com.ims.model.Tenant;
import com.ims.platform.repository.TenantRepository;
import com.ims.shared.auth.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import java.util.Objects;
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
  public TenantResponse getSettings() {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) throw new IllegalStateException("Missing tenant context");

    Tenant tenant =
        Objects.requireNonNull(
            tenantRepository
                .findById(Objects.requireNonNull(tenantId))
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found")));
    return toResponse(tenant);
  }

  @Transactional
  @CacheEvict(value = "tenant", key = "T(com.ims.shared.auth.TenantContext).getTenantId()")
  public TenantResponse updateSettings(UpdateTenantSettingsRequest request) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) throw new IllegalStateException("Missing tenant context");

    Tenant tenant =
        Objects.requireNonNull(
            tenantRepository
                .findById(Objects.requireNonNull(tenantId))
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found")));

    if (request.getWorkspaceSlug() != null
        && !request.getWorkspaceSlug().equals(tenant.getWorkspaceSlug())) {
      throw new IllegalArgumentException("Workspace slug cannot be changed after tenant creation");
    }

    if (request.getName() != null) {
      tenant.setName(request.getName());
    }

    if (request.getInvoiceSequence() != null) {
      tenant.setInvoiceSequence(request.getInvoiceSequence());
    }

    if (request.getExpiryThresholdDays() != null) {
      tenant.setExpiryThresholdDays(request.getExpiryThresholdDays());
    }

    tenant = Objects.requireNonNull(tenantRepository.save(Objects.requireNonNull(tenant)));
    log.info(
        "Tenant settings updated for id={}: workspaceSlug={}", tenantId, tenant.getWorkspaceSlug());
    return toResponse(tenant);
  }

  private TenantResponse toResponse(Tenant tenant) {
    return Objects.requireNonNull(
        TenantResponse.builder()
            .id(tenant.getId())
            .name(tenant.getName())
            .workspaceSlug(tenant.getWorkspaceSlug())
            .businessType(tenant.getBusinessType())
            .plan(tenant.getPlan())
            .status(tenant.getStatus())
            .maxProducts(tenant.getMaxProducts())
            .maxUsers(tenant.getMaxUsers())
            .expiryThresholdDays(tenant.getExpiryThresholdDays())
            .createdAt(tenant.getCreatedAt())
            .build());
  }
}
