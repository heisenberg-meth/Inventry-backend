package com.ims.category;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
  // findById is inherited

  @Override
  @NonNull
  Page<Category> findAll(@NonNull Pageable pageable);

  Page<Category> findByTenantId(Long tenantId, Pageable pageable);

  Optional<Category> findByIdAndTenantId(Long id, Long tenantId);

  boolean existsByNameIgnoreCaseAndTenantId(String name, Long tenantId);

  Optional<Category> findByNameIgnoreCaseAndTenantId(String name, Long tenantId);
}
