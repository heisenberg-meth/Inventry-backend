package com.ims.platform.repository;

import com.ims.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findByDomain(String domain);
    boolean existsByDomain(String domain);
}
