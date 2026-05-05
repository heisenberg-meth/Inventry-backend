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
@Table(name = "support_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SupportMessage extends BaseEntity {

  @Column(name = "ticket_id", nullable = false)
  private Long ticketId;

  @Column(name = "sender_id", nullable = false)
  private Long senderId;

  @Column(name = "sender_type", nullable = false)
  private String senderType;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String message;
}
