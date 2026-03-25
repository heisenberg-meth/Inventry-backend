package com.ims.tenant.repository;

import com.ims.model.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByEmail(String email);

  Optional<User> findByIdAndTenantId(Long id, Long tenantId);

  Page<User> findByTenantId(Long tenantId, Pageable pageable);

  Optional<User> findByIdAndTenantIdIsNull(Long id);

  Page<User> findByTenantIdIsNull(Pageable pageable);

  boolean existsByEmail(String email);
}
