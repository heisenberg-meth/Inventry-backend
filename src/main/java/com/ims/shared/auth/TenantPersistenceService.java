package com.ims.shared.auth;

import com.ims.model.Tenant;
import com.ims.platform.repository.TenantRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@NoArgsConstructor(force = true)
public class TenantPersistenceService {

  private final TenantRepository tenantRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Tenant saveTenant(Tenant tenant) {
    return Objects.requireNonNull(tenantRepository.saveAndFlush(Objects.requireNonNull(tenant))); // commits immediately
  }
}
