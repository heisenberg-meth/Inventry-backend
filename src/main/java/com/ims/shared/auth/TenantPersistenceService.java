package com.ims.shared.auth;

import com.ims.model.Tenant;
import com.ims.platform.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
@Service
@RequiredArgsConstructor
public class TenantPersistenceService {

  private final TenantRepository tenantRepository;

  @Transactional(
      propagation = Propagation.REQUIRES_NEW)
  public Tenant saveTenant(Tenant tenant) {
    Tenant saved = tenantRepository.save(tenant);
    return saved;
  }
}
