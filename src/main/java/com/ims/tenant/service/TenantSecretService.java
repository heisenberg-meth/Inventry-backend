package com.ims.tenant.service;

import com.ims.platform.repository.TenantRepository;
import com.ims.model.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantSecretService {

  private final TenantRepository tenantRepository;

  @Transactional(readOnly = true)
  public String getWebhookSecret(Long tenantId) {
    return tenantRepository.findById(tenantId)
        .map(Tenant::getWebhookSecret)
        .orElse(null);
  }
}
