package com.ims.tenant.service;

import com.ims.model.Tenant;
import com.ims.platform.repository.TenantRepository;
import com.ims.shared.security.SecretEncryptionService;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantSecretService {

  private final TenantRepository tenantRepository;
  private final SecretEncryptionService encryptionService;

  @Transactional(readOnly = true)
  public String getWebhookSecret(Long tenantId) {
    return tenantRepository
        .findById(Objects.requireNonNull(tenantId))
        .map(Tenant::getWebhookSecret)
        .map(encryptionService::decrypt)
        .orElse(null);
  }
}
