package com.ims.tenant.service;

import com.ims.dto.request.UpdateTenantSettingsRequest;
import com.ims.dto.response.TenantResponse;
import com.ims.model.Tenant;
import com.ims.platform.repository.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSettingsService {

  private final TenantRepository tenantRepository;

  @Transactional(readOnly = true)
  public @NonNull TenantResponse getSettings(@NonNull Long tenantId) {
    Tenant tenant =
        Objects.requireNonNull(
            tenantRepository
                .findById(Objects.requireNonNull(tenantId))
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found")));
    return toResponse(tenant);
  }

  @Transactional
  @CacheEvict(value = "tenant", key = "#tenantId")
  public @NonNull TenantResponse updateSettings(@NonNull Long tenantId, @NonNull UpdateTenantSettingsRequest request) {
    Tenant tenant =
        Objects.requireNonNull(
            tenantRepository
                .findById(Objects.requireNonNull(tenantId))
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found")));

    if (request.getWorkspaceSlug() != null && !request.getWorkspaceSlug().equals(tenant.getWorkspaceSlug())) {
      if (tenantRepository.existsByWorkspaceSlug(request.getWorkspaceSlug())) {
        throw new IllegalArgumentException("Workspace slug already taken");
      }
      tenant.setWorkspaceSlug(request.getWorkspaceSlug());
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
    log.info("Tenant settings updated for id={}: workspaceSlug={}", tenantId, tenant.getWorkspaceSlug());
    return toResponse(tenant);
  }

  private @NonNull TenantResponse toResponse(@NonNull Tenant tenant) {
    return Objects.requireNonNull(TenantResponse.builder()
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
