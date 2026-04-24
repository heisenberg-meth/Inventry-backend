package com.ims.platform.controller;

import com.ims.dto.request.AddMessageRequest;
import com.ims.dto.request.AssignTicketRequest;
import com.ims.dto.request.UpdateTicketStatusRequest;
import com.ims.model.SupportMessage;
import com.ims.model.SupportTicket;
import com.ims.shared.auth.JwtAuthDetails;
import com.ims.shared.rbac.RequiresRole;
import com.ims.tenant.service.SupportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/support/tickets")
@RequiredArgsConstructor
@Tag(name = "Platform - Support", description = "Platform-side support ticket management")
@SecurityRequirement(name = "bearerAuth")
public class PlatformSupportController {

  private final SupportService supportService;

  @GetMapping
  @RequiresRole({"ROOT", "PLATFORM_ADMIN", "SUPPORT_ADMIN"})
  @Operation(summary = "List all support tickets")
  public ResponseEntity<Page<SupportTicket>> listAll(@NonNull Pageable pageable) {
    return ResponseEntity.ok(supportService.listAllTickets(pageable));
  }

  @GetMapping("/my-tickets")
  @RequiresRole({"SUPPORT_ADMIN"})
  @Operation(summary = "List tickets assigned to me")
  public ResponseEntity<Page<SupportTicket>> myTickets(@NonNull Pageable pageable) {
    JwtAuthDetails auth = getAuthDetails();
    return ResponseEntity.ok(
        supportService.listMyAssignedTickets(Objects.requireNonNull(auth.getUserId()), pageable));
  }

  @GetMapping("/{id}")
  @RequiresRole({"ROOT", "PLATFORM_ADMIN", "SUPPORT_ADMIN"})
  @Operation(summary = "Get ticket details with messages")
  public ResponseEntity<Map<String, Object>> getDetails(@NonNull @PathVariable Long id) {
    return ResponseEntity.ok(supportService.getPlatformTicketDetails(id));
  }

  @PostMapping("/{id}/reply")
  @RequiresRole({"ROOT", "PLATFORM_ADMIN", "SUPPORT_ADMIN"})
  @Operation(summary = "Reply to a ticket")
  public ResponseEntity<SupportMessage> reply(
      @NonNull @PathVariable Long id, @NonNull @Valid @RequestBody AddMessageRequest request) {
    JwtAuthDetails auth = getAuthDetails();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            supportService.addMessage(
                id, Objects.requireNonNull(auth.getUserId()), "PLATFORM", request));
  }

  @PatchMapping("/{id}/assign")
  @RequiresRole({"ROOT", "PLATFORM_ADMIN"})
  @Operation(summary = "Assign ticket to support admin")
  public ResponseEntity<SupportTicket> assignTicket(
      @NonNull @PathVariable Long id, @NonNull @Valid @RequestBody AssignTicketRequest request) {
    return ResponseEntity.ok(supportService.assignTicket(id, request));
  }

  @PatchMapping("/{id}/status")
  @RequiresRole({"ROOT", "PLATFORM_ADMIN", "SUPPORT_ADMIN"})
  @Operation(summary = "Update ticket status")
  public ResponseEntity<SupportTicket> updateStatus(
      @NonNull @PathVariable Long id,
      @NonNull @Valid @RequestBody UpdateTicketStatusRequest request) {
    return ResponseEntity.ok(supportService.updateStatus(id, request));
  }

  @PatchMapping("/{id}/close")
  @RequiresRole({"ROOT", "PLATFORM_ADMIN", "SUPPORT_ADMIN"})
  @Operation(summary = "Close a ticket")
  public ResponseEntity<SupportTicket> closeTicket(@NonNull @PathVariable Long id) {
    return ResponseEntity.ok(supportService.closeTicketByPlatform(id));
  }

  @GetMapping("/stats")
  @RequiresRole({"ROOT", "PLATFORM_ADMIN"})
  @Operation(summary = "Get ticket stats")
  public ResponseEntity<Map<String, Object>> getStats() {
    return ResponseEntity.ok(supportService.getStats());
  }

  private JwtAuthDetails getAuthDetails() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getDetails() instanceof JwtAuthDetails details) {
      return details;
    }
    throw new com.ims.shared.exception.UnauthorizedAccessException("User not authenticated");
  }
}
