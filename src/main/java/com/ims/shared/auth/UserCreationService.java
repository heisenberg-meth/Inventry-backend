package com.ims.shared.auth;

import com.ims.model.User;
import com.ims.tenant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserCreationService {

  private final UserRepository userRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void createUserForTenant(User user, Long tenantId) {
    try {
      TenantContext.set(tenantId);
      userRepository.save(user);
    } finally {
      TenantContext.clear();
    }
  }
}
