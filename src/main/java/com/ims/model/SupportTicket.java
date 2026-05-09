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
@Table(name = "support_tickets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SupportTicket extends BaseEntity {

  @Column(name = "created_by", nullable = false)
  private Long createdBy;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String description;

  @Column @Builder.Default private String priority = "MEDIUM";

  @Column @Builder.Default private String status = "OPEN";

  @Column @Builder.Default private String category = "GENERAL";

  @Column(name = "assigned_to")
  private Long assignedTo;
}
