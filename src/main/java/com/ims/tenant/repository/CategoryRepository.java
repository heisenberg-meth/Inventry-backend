package com.ims.tenant.repository;

import com.ims.model.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByIdAndTenantId(Long id, Long tenantId);
    Page<Category> findByTenantId(Long tenantId, Pageable pageable);
}
