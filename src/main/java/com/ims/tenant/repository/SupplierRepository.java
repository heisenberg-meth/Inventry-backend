package com.ims.tenant.repository;

import com.ims.model.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {
  // findById is inherited

  @Override
  @NonNull
  Page<Supplier> findAll(@NonNull Pageable pageable);
}
