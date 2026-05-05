package com.ims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "payment_gateway_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PaymentGatewayLog extends BaseEntity {

  @Column(name = "payment_id")
  private Long paymentId;

  @Column(name = "event_type")
  private String eventType;

  @Column(name = "raw_payload", columnDefinition = "TEXT")
  private String rawPayload;
}
