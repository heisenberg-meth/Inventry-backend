package com.ims.shared.notification;

import com.ims.model.Alert;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
  @Query("SELECT a FROM Alert a WHERE a.tenantId = :tenantId AND a.isDismissed = false")
  List<Alert> findAllByTenantIdAndIsDismissedFalse(@Param("tenantId") Long tenantId);

  @Query("SELECT a FROM Alert a WHERE a.tenantId = :tenantId AND a.type = :type AND a.resourceId = :resourceId AND a.isDismissed = false")
  Optional<Alert> findByTypeAndResourceIdAndTenantIdAndIsDismissedFalse(
      @Param("type") String type, @Param("resourceId") Long resourceId, @Param("tenantId") Long tenantId);

  @Query("SELECT a FROM Alert a WHERE a.id = :id AND a.tenantId = :tenantId")
  Optional<Alert> findByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
