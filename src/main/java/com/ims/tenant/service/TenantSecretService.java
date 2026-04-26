package com.ims.tenant.service;

import com.ims.model.Tenant;
import com.ims.platform.repository.TenantRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantSecretService {

  private final TenantRepository tenantRepository;

  @Transactional(readOnly = true)
  public String getWebhookSecret(@NonNull Long tenantId) {
    return tenantRepository
        .findById(Objects.requireNonNull(tenantId))
        .map(Tenant::getWebhookSecret)
        .orElse(null);
  }
}
