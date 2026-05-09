package com.ims.shared.notification;

import com.ims.model.Alert;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
  List<Alert> findByTenantIdAndIsDismissedFalse(Long tenantId);

  Optional<Alert> findByTypeAndResourceIdAndIsDismissedFalse(String type, Long resourceId);
}
