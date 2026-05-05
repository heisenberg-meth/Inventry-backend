package com.ims.tenant.controller;

import com.ims.model.Notification;
import com.ims.shared.notification.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenant/notifications")
@RequiredArgsConstructor
@Tag(name = "Tenant - Notifications", description = "In-app notification feed")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

  private final NotificationService notificationService;

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "List my notifications")
  public ResponseEntity<List<Notification>> list() {
    return ResponseEntity.ok(notificationService.getMyNotifications(extractUserId()));
  }

  @GetMapping("/unread")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "List my unread notifications")
  public ResponseEntity<List<Notification>> listUnread() {
    return ResponseEntity.ok(notificationService.getUnreadNotifications(extractUserId()));
  }

  @PatchMapping("/{id}/read")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "Mark notification as read")
  public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
    notificationService.markAsRead(id);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/mark-all-read")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "Mark all notifications as read")
  public ResponseEntity<Void> markAllRead() {
    notificationService.markAllAsRead(extractUserId());
    return ResponseEntity.noContent().build();
  }

  private Long extractUserId() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof com.ims.shared.auth.JwtAuthenticationToken jwtAuth) {
      return jwtAuth.getUserId();
    }
    return null;
  }
}
