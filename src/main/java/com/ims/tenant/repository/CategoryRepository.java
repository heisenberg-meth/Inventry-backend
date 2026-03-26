package com.ims.tenant.repository;

import com.ims.model.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
  // findById is inherited

  Page<Category> findAll(Pageable pageable);

  boolean existsByNameIgnoreCase(String name);
}
