package com.ims.category;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

  @Override
  Page<Category> findAll(Pageable pageable);

  boolean existsByNameIgnoreCase(String name);

  Optional<Category> findByNameIgnoreCase(String name);
}
