package com.ims.shared.auth;

import com.ims.model.Tenant;
import com.ims.platform.repository.TenantRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantPersistenceService {

  private final TenantRepository tenantRepository;

  @org.springframework.transaction.annotation.Transactional(
      propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
  public @NonNull Tenant saveTenant(@NonNull Tenant tenant) {
    Tenant saved = tenantRepository.save(Objects.requireNonNull(tenant));
    return Objects.requireNonNull(saved);
  }
}
