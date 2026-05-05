package com.ims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "webhooks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class Webhook extends BaseEntity {

  @Column(nullable = false, columnDefinition = "TEXT")
  private String url;

  @Column
  private String secret;

  @Column(name = "event_types", nullable = false, columnDefinition = "TEXT")
  private String eventTypes;

  @Column(name = "is_active")
  @Builder.Default
  private Boolean isActive = true;
}
