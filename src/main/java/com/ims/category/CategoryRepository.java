package com.ims.category;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

  @Query("SELECT c FROM Category c WHERE c.tenantId = :tenantId")
  Page<Category> findByTenantId(@Param("tenantId") Long tenantId, Pageable pageable);

  @Query("SELECT c FROM Category c WHERE c.tenantId = :tenantId")
  List<Category> findAllByTenantId(@Param("tenantId") Long tenantId);

  @Query("SELECT COUNT(c) > 0 FROM Category c WHERE c.tenantId = :tenantId AND LOWER(c.name) = LOWER(:name)")
  boolean existsByNameIgnoreCaseAndTenantId(@Param("name") String name, @Param("tenantId") Long tenantId);

  @Query("SELECT c FROM Category c WHERE c.id = :id AND c.tenantId = :tenantId")
  Optional<Category> findByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);

  @Query("SELECT c FROM Category c WHERE c.tenantId = :tenantId AND LOWER(c.name) = LOWER(:name)")
  Optional<Category> findByNameIgnoreCaseAndTenantId(@Param("name") String name, @Param("tenantId") Long tenantId);
}
