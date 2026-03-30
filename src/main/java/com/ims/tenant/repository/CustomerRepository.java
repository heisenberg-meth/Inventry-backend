package com.ims.tenant.repository;

import com.ims.model.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
  // findById is inherited

  @Override
  @NonNull
  Page<Customer> findAll(@NonNull Pageable pageable);
}
