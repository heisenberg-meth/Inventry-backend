package com.ims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "support_attachments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportAttachment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "ticket_id", nullable = false)
  private Long ticketId;

  @Column(name = "file_url", nullable = false)
  private String fileUrl;

  @Column(name = "uploaded_by", nullable = false)
  private Long uploadedBy;

  @Column(name = "created_at")
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();
}
