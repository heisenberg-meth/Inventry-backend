package com.ims.tenant.controller;

import com.ims.dto.request.AddMessageRequest;
import com.ims.dto.request.CreateTicketRequest;
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
@RequestMapping("/tenant/support/tickets")
@RequiredArgsConstructor
@Tag(name = "Tenant - Support", description = "Tenant-side support ticket management")
@SecurityRequirement(name = "bearerAuth")
public class TenantSupportController {

  private final SupportService supportService;

  @PostMapping
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "Create support ticket")
  public ResponseEntity<SupportTicket> createTicket(
      @NonNull @Valid @RequestBody CreateTicketRequest request) {
    JwtAuthDetails auth = getAuthDetails();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(supportService.createTicket(
            Objects.requireNonNull(auth.getTenantId()),
            Objects.requireNonNull(auth.getUserId()), request));
  }

  @GetMapping
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "List my tenant tickets")
  public ResponseEntity<Page<SupportTicket>> listTickets(@NonNull Pageable pageable) {
    JwtAuthDetails auth = getAuthDetails();
    return ResponseEntity.ok(
        supportService.listTenantTickets(Objects.requireNonNull(auth.getTenantId()), pageable));
  }

  @GetMapping("/{id}")
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "Get ticket details")
  public ResponseEntity<Map<String, Object>> getTicketDetails(@NonNull @PathVariable Long id) {
    JwtAuthDetails auth = getAuthDetails();
    return ResponseEntity.ok(
        supportService.getTenantTicketDetails(Objects.requireNonNull(auth.getTenantId()), id));
  }

  @PostMapping("/{id}/messages")
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "Add message to ticket")
  public ResponseEntity<SupportMessage> addMessage(
      @NonNull @PathVariable Long id, @NonNull @Valid @RequestBody AddMessageRequest request) {
    JwtAuthDetails auth = getAuthDetails();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(supportService.addMessage(
            id, Objects.requireNonNull(auth.getUserId()), "TENANT", request));
  }

  @PatchMapping("/{id}/close")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Close ticket")
  public ResponseEntity<SupportTicket> closeTicket(@NonNull @PathVariable Long id) {
    JwtAuthDetails auth = getAuthDetails();
    return ResponseEntity.ok(
        supportService.closeTicketByTenant(Objects.requireNonNull(auth.getTenantId()), id));
  }

  private JwtAuthDetails getAuthDetails() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getDetails() instanceof JwtAuthDetails details) {
      return details;
    }
    throw new com.ims.shared.exception.UnauthorizedAccessException("User not authenticated");
  }
}
