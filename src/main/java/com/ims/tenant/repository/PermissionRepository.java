package com.ims.tenant.repository;

import com.ims.model.Permission;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

  Optional<Permission> findByKey(String key);

  List<Permission> findAllByOrderByKeyAsc();

  List<Permission> findByIdIn(List<Long> ids);
}
