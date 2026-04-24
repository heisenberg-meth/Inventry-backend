package com.ims.tenant.service;

import com.ims.dto.request.AddMessageRequest;
import com.ims.dto.request.AssignTicketRequest;
import com.ims.dto.request.CreateTicketRequest;
import com.ims.dto.request.UpdateTicketStatusRequest;
import com.ims.model.SupportMessage;
import com.ims.model.SupportTicket;
import com.ims.shared.audit.AuditAction;
import com.ims.shared.audit.AuditLogService;
import com.ims.tenant.repository.SupportMessageRepository;
import com.ims.tenant.repository.SupportTicketRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupportService {

  private final SupportTicketRepository ticketRepository;
  private final SupportMessageRepository messageRepository;
  private final AuditLogService auditLogService;

  // ==================== TENANT-SIDE ====================

  @Transactional
  public @NonNull SupportTicket createTicket(
      @NonNull Long tenantId, @NonNull Long userId, @NonNull CreateTicketRequest request) {

    SupportTicket ticket =
        SupportTicket.builder()
            .tenantId(tenantId)
            .createdBy(userId)
            .title(request.getTitle())
            .description(request.getDescription())
            .priority(request.getPriority() != null ? request.getPriority() : "MEDIUM")
            .category(request.getCategory() != null ? request.getCategory() : "GENERAL")
            .status("OPEN")
            .build();

    SupportTicket saved = ticketRepository.save(ticket);
    Objects.requireNonNull(saved);

    auditLogService.log(
        AuditAction.CREATE_TICKET, tenantId, userId, "Created ticket: " + saved.getTitle());
    log.info("Support ticket created: id={}", saved.getId());
    return Objects.requireNonNull(saved);
  }

  @Transactional(readOnly = true)
  public Page<SupportTicket> listTenantTickets(@NonNull Long tenantId, @NonNull Pageable pageable) {
    return ticketRepository.findByTenantId(tenantId, pageable);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getTenantTicketDetails(
      @NonNull Long tenantId, @NonNull Long ticketId) {
    SupportTicket ticket =
        ticketRepository
            .findByIdAndTenantId(ticketId, tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Ticket not found"));

    List<SupportMessage> messages = messageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);

    Map<String, Object> details = new HashMap<>();
    details.put("ticket", ticket);
    details.put("messages", messages);
    return details;
  }

  @Transactional
  public SupportTicket closeTicketByTenant(@NonNull Long tenantId, @NonNull Long ticketId) {
    SupportTicket ticket =
        ticketRepository
            .findByIdAndTenantId(ticketId, tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Ticket not found"));

    ticket.setStatus("CLOSED");
    ticket.setUpdatedAt(LocalDateTime.now());
    SupportTicket saved = ticketRepository.save(ticket);

    auditLogService.log(
        AuditAction.CLOSE_TICKET, tenantId, null, "Tenant closed ticket: #" + ticketId);
    return saved;
  }

  // ==================== PLATFORM-SIDE ====================

  @Transactional(readOnly = true)
  public Page<SupportTicket> listAllTickets(@NonNull Pageable pageable) {
    return ticketRepository.findAll(pageable);
  }

  @Transactional(readOnly = true)
  public Page<SupportTicket> listMyAssignedTickets(
      @NonNull Long userId, @NonNull Pageable pageable) {
    return ticketRepository.findByAssignedTo(userId, pageable);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getPlatformTicketDetails(@NonNull Long ticketId) {
    SupportTicket ticket =
        ticketRepository
            .findById(ticketId)
            .orElseThrow(() -> new EntityNotFoundException("Ticket not found"));

    List<SupportMessage> messages = messageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);

    Map<String, Object> details = new HashMap<>();
    details.put("ticket", ticket);
    details.put("messages", messages);
    return details;
  }

  @Transactional
  public SupportTicket assignTicket(@NonNull Long ticketId, @NonNull AssignTicketRequest request) {
    SupportTicket ticket =
        ticketRepository
            .findById(ticketId)
            .orElseThrow(() -> new EntityNotFoundException("Ticket not found"));

    ticket.setAssignedTo(request.getSupportAdminId());
    if ("OPEN".equals(ticket.getStatus())) {
      ticket.setStatus("IN_PROGRESS");
    }
    ticket.setUpdatedAt(LocalDateTime.now());
    SupportTicket saved = ticketRepository.save(ticket);

    auditLogService.log(
        AuditAction.ASSIGN_TICKET,
        ticket.getTenantId(),
        request.getSupportAdminId(),
        "Ticket #" + ticketId + " assigned to user " + request.getSupportAdminId());

    return saved;
  }

  @Transactional
  public SupportTicket updateStatus(
      @NonNull Long ticketId, @NonNull UpdateTicketStatusRequest request) {
    SupportTicket ticket =
        ticketRepository
            .findById(ticketId)
            .orElseThrow(() -> new EntityNotFoundException("Ticket not found"));

    ticket.setStatus(request.getStatus());
    ticket.setUpdatedAt(LocalDateTime.now());
    SupportTicket saved = ticketRepository.save(ticket);

    auditLogService.log(
        AuditAction.UPDATE_TICKET_STATUS,
        ticket.getTenantId(),
        null,
        "Ticket #" + ticketId + " status changed to " + request.getStatus());

    return saved;
  }

  @Transactional
  public SupportTicket closeTicketByPlatform(@NonNull Long ticketId) {
    SupportTicket ticket =
        ticketRepository
            .findById(ticketId)
            .orElseThrow(() -> new EntityNotFoundException("Ticket not found"));

    ticket.setStatus("CLOSED");
    ticket.setUpdatedAt(LocalDateTime.now());
    SupportTicket saved = ticketRepository.save(ticket);

    auditLogService.log(
        AuditAction.CLOSE_TICKET,
        ticket.getTenantId(),
        null,
        "Platform closed ticket: #" + ticketId);
    return saved;
  }

  // ==================== SHARED ====================

  @Transactional
  public @NonNull SupportMessage addMessage(
      @NonNull Long ticketId,
      @NonNull Long senderId,
      @NonNull String senderType,
      @NonNull AddMessageRequest request) {

    SupportTicket ticket =
        ticketRepository
            .findById(ticketId)
            .orElseThrow(() -> new EntityNotFoundException("Ticket not found"));

    SupportMessage message =
        SupportMessage.builder()
            .ticketId(ticketId)
            .senderId(senderId)
            .senderType(senderType)
            .message(request.getMessage())
            .build();

    SupportMessage saved = messageRepository.save(message);
    Objects.requireNonNull(saved);

    // Reopen if CLOSED and tenant messages
    if ("CLOSED".equals(ticket.getStatus()) && "TENANT".equals(senderType)) {
      ticket.setStatus("REOPENED");
      ticket.setUpdatedAt(LocalDateTime.now());
      ticketRepository.save(ticket);
    }

    return Objects.requireNonNull(saved);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getStats() {
    Map<String, Object> stats = new HashMap<>();
    stats.put("total", ticketRepository.count());
    stats.put("open", ticketRepository.countByStatus("OPEN"));
    stats.put("inProgress", ticketRepository.countByStatus("IN_PROGRESS"));
    stats.put("closed", ticketRepository.countByStatus("CLOSED"));
    stats.put("reopened", ticketRepository.countByStatus("REOPENED"));
    return stats;
  }
}
