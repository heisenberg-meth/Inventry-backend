package com.ims.tenant.repository;

import com.ims.model.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

  List<Role> findByTenantIdOrderByNameAsc(Long tenantId);

  Optional<Role> findByIdAndTenantId(Long id, Long tenantId);

  Optional<Role> findByNameAndTenantId(String name, Long tenantId);

  Optional<Role> findByNameAndTenantIdIsNull(String name);
}
