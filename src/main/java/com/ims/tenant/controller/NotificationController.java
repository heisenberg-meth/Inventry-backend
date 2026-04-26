package com.ims.tenant.controller;

import com.ims.model.Notification;
import com.ims.shared.notification.NotificationService;
import com.ims.shared.rbac.RequiresRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/notifications")
@RequiredArgsConstructor
@Tag(name = "Tenant - Notifications", description = "In-app notification feed")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

  private final NotificationService notificationService;

  @GetMapping
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "List my notifications")
  public ResponseEntity<List<Notification>> list() {
    return ResponseEntity.ok(notificationService.getMyNotifications(Objects.requireNonNull(extractUserId())));
  }

  @GetMapping("/unread")
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "List my unread notifications")
  public ResponseEntity<List<Notification>> listUnread() {
    return ResponseEntity.ok(notificationService.getUnreadNotifications(Objects.requireNonNull(extractUserId())));
  }

  @PatchMapping("/{id}/read")
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "Mark notification as read")
  public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
    notificationService.markAsRead(Objects.requireNonNull(id));
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/mark-all-read")
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "Mark all notifications as read")
  public ResponseEntity<Void> markAllRead() {
    notificationService.markAllAsRead(Objects.requireNonNull(extractUserId()));
    return ResponseEntity.noContent().build();
  }

  private Long extractUserId() {
    var auth = Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication(), "Authentication required");
    return Objects.requireNonNull((Long) auth.getPrincipal(), "User ID principal required");
  }
}
