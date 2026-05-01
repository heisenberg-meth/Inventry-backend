package com.ims.tenant.service;

import com.ims.dto.request.AddMessageRequest;
import com.ims.dto.request.AssignTicketRequest;
import com.ims.dto.request.CreateTicketRequest;
import com.ims.dto.request.UpdateTicketStatusRequest;
import com.ims.model.SupportMessage;
import com.ims.model.SupportTicket;
import com.ims.model.SupportTicketStatus;
import com.ims.shared.audit.AuditAction;
import com.ims.shared.audit.AuditLogService;
import com.ims.shared.audit.AuditResource;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.repository.SupportMessageRepository;
import com.ims.tenant.repository.SupportTicketRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
  public SupportTicket createTicket(
      Long userId, CreateTicketRequest request) {
    Long tenantId = com.ims.shared.auth.TenantContext.requireTenantId();

    SupportTicket initialTicket = SupportTicket.builder()
        .createdBy(userId)
        .title(request.getTitle())
        .description(request.getDescription())
        .priority(request.getPriority() != null ? request.getPriority() : "MEDIUM")
        .category(request.getCategory() != null ? request.getCategory() : "GENERAL")
        .status(SupportTicketStatus.OPEN)
        .build();

    var savedTicket = ticketRepository.save(initialTicket);

    auditLogService.log(
        AuditAction.CREATE_TICKET, tenantId, userId, "Created ticket: " + savedTicket.getTitle());
    log.info("Support ticket created: id={}", savedTicket.getId());
    return savedTicket;
  }

  @Transactional(readOnly = true)
  public Page<SupportTicket> listTenantTickets(Pageable pageable) {
    TenantContext.assertTenantPresent();
    return Objects.requireNonNull(ticketRepository.findAll(pageable));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getTenantTicketDetails(Long ticketId) {
    TenantContext.assertTenantPresent();
    SupportTicket ticket = ticketRepository
        .findById(ticketId)
        .orElseThrow(() -> new EntityNotFoundException("Ticket not found"));

    List<SupportMessage> messages = messageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);

    Map<String, Object> details = new HashMap<>();
    details.put("ticket", ticket);
    details.put("messages", messages);
    return Objects.requireNonNull(details);
  }

  @Transactional
  public SupportTicket closeTicketByTenant(Long ticketId) {
    TenantContext.assertTenantPresent();
    SupportTicket ticket = ticketRepository
        .findById(ticketId)
        .orElseThrow(() -> new EntityNotFoundException("Ticket not found"));

    ticket.setStatus(SupportTicketStatus.CLOSED);
    ticket.setUpdatedAt(Objects.requireNonNull(LocalDateTime.now()));
    SupportTicket saved = Objects.requireNonNull(ticketRepository.save(ticket));

    auditLogService.logAudit(
        AuditAction.CLOSE_TICKET, AuditResource.SUPPORT_TICKET, ticketId, "Tenant closed ticket: #" + ticketId);
    return saved;
  }

  // ==================== PLATFORM-SIDE ====================

  @Transactional(readOnly = true)
  public Page<SupportTicket> listAllTickets(Pageable pageable) {
    return Objects.requireNonNull(ticketRepository.findAll(pageable));
  }

  @Transactional(readOnly = true)
  public Page<SupportTicket> listMyAssignedTickets(
      Long userId, Pageable pageable) {
    return Objects.requireNonNull(ticketRepository.findByAssignedTo(userId, pageable));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getPlatformTicketDetails(Long ticketId) {
    SupportTicket ticket = ticketRepository
        .findById(ticketId)
        .orElseThrow(() -> new EntityNotFoundException("Ticket not found"));

    List<SupportMessage> messages = messageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);

    Map<String, Object> details = new HashMap<>();
    details.put("ticket", ticket);
    details.put("messages", messages);
    return details;
  }

  @Transactional
  public SupportTicket assignTicket(Long ticketId, AssignTicketRequest request) {
    SupportTicket ticket = ticketRepository
        .findById(ticketId)
        .orElseThrow(() -> new EntityNotFoundException("Ticket not found"));

    ticket.setAssignedTo(request.getSupportAdminId());
    if (SupportTicketStatus.OPEN.equals(ticket.getStatus())) {
      ticket.setStatus(SupportTicketStatus.IN_PROGRESS);
    }
    ticket.setUpdatedAt(Objects.requireNonNull(LocalDateTime.now()));
    SupportTicket saved = Objects.requireNonNull(ticketRepository.save(ticket));

    auditLogService.logAudit(
        AuditAction.ASSIGN_TICKET,
        AuditResource.SUPPORT_TICKET,
        ticketId,
        "Ticket #" + ticketId + " assigned to user " + request.getSupportAdminId());

    return saved;
  }

  @Transactional
  public SupportTicket updateStatus(
      Long ticketId, UpdateTicketStatusRequest request) {
    SupportTicket ticket = ticketRepository
        .findById(ticketId)
        .orElseThrow(() -> new EntityNotFoundException("Ticket not found"));

    ticket.setStatus(request.getStatus());
    ticket.setUpdatedAt(Objects.requireNonNull(LocalDateTime.now()));
    SupportTicket saved = Objects.requireNonNull(ticketRepository.save(ticket));

    auditLogService.logAudit(
        AuditAction.UPDATE_TICKET_STATUS,
        AuditResource.SUPPORT_TICKET,
        ticketId,
        "Ticket #" + ticketId + " status changed to " + request.getStatus());

    return saved;
  }

  @Transactional
  public SupportTicket closeTicketByPlatform(Long ticketId) {
    SupportTicket ticket = ticketRepository
        .findById(ticketId)
        .orElseThrow(() -> new EntityNotFoundException("Ticket not found"));

    ticket.setStatus(SupportTicketStatus.CLOSED);
    ticket.setUpdatedAt(Objects.requireNonNull(LocalDateTime.now()));
    SupportTicket saved = Objects.requireNonNull(ticketRepository.save(ticket));

    auditLogService.logAudit(
        AuditAction.CLOSE_TICKET,
        AuditResource.SUPPORT_TICKET,
        ticketId,
        "Platform closed ticket: #" + ticketId);
    return saved;
  }

  // ==================== SHARED ====================

  @Transactional
  public SupportMessage addMessage(
      Long ticketId,
      Long senderId,
      String senderType,
      AddMessageRequest request) {

    SupportTicket ticket = ticketRepository
        .findById(ticketId)
        .orElseThrow(() -> new EntityNotFoundException("Ticket not found"));

    SupportMessage message = SupportMessage.builder()
        .ticketId(ticketId)
        .senderId(senderId)
        .senderType(senderType)
        .message(request.getMessage())
        .build();

    SupportMessage saved = messageRepository.save(message);

    // Reopen if CLOSED and tenant messages
    if (SupportTicketStatus.CLOSED.equals(ticket.getStatus()) && "TENANT".equals(senderType)) {
      ticket.setStatus(SupportTicketStatus.REOPENED);
      ticket.setUpdatedAt(Objects.requireNonNull(LocalDateTime.now()));
      ticketRepository.save(ticket);
    }

    return saved;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getStats() {
    Map<String, Object> stats = new HashMap<>();
    stats.put("total", ticketRepository.count());
    stats.put("open", ticketRepository.countByStatus(SupportTicketStatus.OPEN));
    stats.put("inProgress", ticketRepository.countByStatus(SupportTicketStatus.IN_PROGRESS));
    stats.put("closed", ticketRepository.countByStatus(SupportTicketStatus.CLOSED));
    stats.put("reopened", ticketRepository.countByStatus(SupportTicketStatus.REOPENED));
    return Objects.requireNonNull(stats);
  }
}
