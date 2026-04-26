package com.ims.shared.auth;

import com.ims.model.Tenant;
import com.ims.platform.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantPersistenceService {

  private final TenantRepository tenantRepository;

  @org.springframework.transaction.annotation.Transactional(
      propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
  public Tenant saveTenant(Tenant tenant) {
    Tenant saved = tenantRepository.save(tenant);
    return saved;
  }
}
