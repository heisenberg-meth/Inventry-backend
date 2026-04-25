package com.ims.platform.controller;

import com.ims.model.Subscription;
import com.ims.platform.service.SubscriptionService;
import com.ims.shared.rbac.RequiresRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Platform - Subscriptions", description = "Global subscription management")
@SecurityRequirement(name = "bearerAuth")
public class SubscriptionController {

  /** Default number of days added to a subscription when the request omits "days". */
  private static final int DEFAULT_EXTENSION_DAYS = 30;

  private final SubscriptionService subscriptionService;

  @GetMapping
  @RequiresRole({"ROOT", "PLATFORM_ADMIN"})
  @Operation(summary = "List all active subscriptions")
  public ResponseEntity<List<Subscription>> list() {
    return ResponseEntity.ok(subscriptionService.getActiveSubscriptions());
  }

  @PostMapping("/{id}/extend")
  @RequiresRole({"ROOT"})
  @Operation(summary = "Extend subscription duration")
  public ResponseEntity<Subscription> extend(
      @PathVariable @NonNull Long id, @RequestBody Map<String, Integer> body) {
    int days = body.getOrDefault("days", DEFAULT_EXTENSION_DAYS);
    return ResponseEntity.ok(subscriptionService.extendSubscription(id, days));
  }
}
