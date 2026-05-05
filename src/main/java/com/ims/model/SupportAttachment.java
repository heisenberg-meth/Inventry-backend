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
@Table(name = "support_attachments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SupportAttachment extends BaseEntity {

  @Column(name = "ticket_id", nullable = false)
  private Long ticketId;

  @Column(name = "file_url", nullable = false)
  private String fileUrl;

  @Column(name = "uploaded_by", nullable = false)
  private Long uploadedBy;
}
