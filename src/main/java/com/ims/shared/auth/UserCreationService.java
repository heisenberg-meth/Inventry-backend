package com.ims.shared.auth;

import com.ims.model.User;
import com.ims.tenant.repository.UserRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserCreationService {

  private final UserRepository userRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public User createUserForTenant(User user, Long tenantId) {
    Long oldTenantId = TenantContext.getTenantId();
    try {
      TenantContext.setTenantId(Objects.requireNonNull(tenantId));
      return userRepository.save(Objects.requireNonNull(user));
    } finally {
      if (oldTenantId == null) {
        TenantContext.clear();
      } else {
        TenantContext.setTenantId(oldTenantId);
      }
    }
  }
}
