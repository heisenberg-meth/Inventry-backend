package com.ims.shared.payment;

import com.ims.model.PaymentGatewayLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentGatewayLogRepository extends JpaRepository<PaymentGatewayLog, Long> {
  boolean existsByEventId(String eventId);
}
