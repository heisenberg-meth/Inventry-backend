package com.ims.tenant.repository;

import com.ims.model.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByEmail(String email);

  @Query(value = "SELECT * FROM users WHERE email = :email", nativeQuery = true)
  Optional<User> findByEmailUnfiltered(@Param("email") String email);

  @Query(value = "SELECT * FROM users WHERE id = :id", nativeQuery = true)
  Optional<User> findByIdUnfiltered(@Param("id") Long id);

  // findById is inherited

  Optional<User> findByIdAndTenantIdIsNull(Long id);

  Page<User> findByTenantIdIsNull(Pageable pageable);

  boolean existsByEmail(String email);
}
