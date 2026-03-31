package com.ims.shared.auth;

import com.ims.model.User;
import com.ims.tenant.repository.UserRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserCreationService {

  private final UserRepository userRepository;

  @Transactional(propagation = Propagation.REQUIRED)
  public void createUserForTenant(@NonNull User user, @NonNull Long tenantId) {
    try {
      TenantContext.set(Objects.requireNonNull(tenantId));
      userRepository.save(Objects.requireNonNull(user));
    } finally {
      TenantContext.clear();
    }
  }
}
