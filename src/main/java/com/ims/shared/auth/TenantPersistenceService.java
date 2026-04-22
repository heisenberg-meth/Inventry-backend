package com.ims.shared.auth;

import com.ims.model.Tenant;
import com.ims.platform.repository.TenantRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantPersistenceService {

  private final TenantRepository tenantRepository;

  @Transactional
  public @NonNull Tenant saveTenant(@NonNull Tenant tenant) {
    return Objects.requireNonNull(tenantRepository.saveAndFlush(Objects.requireNonNull(tenant))); // commits immediately
  }
}
