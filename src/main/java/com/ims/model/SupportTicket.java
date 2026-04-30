package com.ims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "support_tickets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportTicket {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "created_by", nullable = false)
  private Long createdBy;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String description;

  @Column
  @Builder.Default
  private String priority = "MEDIUM";

  @Column
  @Enumerated(EnumType.STRING)
  @Builder.Default
  private SupportTicketStatus status = SupportTicketStatus.OPEN;

  @Column
  @Builder.Default
  private String category = "GENERAL";

  @Column(name = "assigned_to")
  private Long assignedTo;

  @Column(name = "created_at")
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(name = "updated_at")
  @Builder.Default
  private LocalDateTime updatedAt = LocalDateTime.now();
}
