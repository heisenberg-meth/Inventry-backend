package com.ims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "alerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class Alert extends BaseEntity {

  @Column(nullable = false)
  private String type;

  @Column(nullable = false)
  private String severity;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String message;

  @Column(name = "resource_id")
  private Long resourceId;

  @Column(name = "is_dismissed")
  @Builder.Default
  private Boolean isDismissed = false;

  @Column(name = "dismissed_at")
  private LocalDateTime dismissedAt;
}
