package com.ims.tenant.controller;

import com.ims.model.Webhook;
import com.ims.shared.rbac.RequiresRole;
import com.ims.shared.webhook.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenant/settings/webhooks")
@RequiredArgsConstructor
@Tag(name = "Tenant - Webhooks", description = "Configure external webhook alerts")
@SecurityRequirement(name = "bearerAuth")
public class WebhookController {

  private final WebhookService webhookService;

  @GetMapping
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "List all configured webhooks")
  public ResponseEntity<List<Webhook>> list() {
    return ResponseEntity.ok(webhookService.getMyWebhooks());
  }

  @PostMapping
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Create new webhook")
  public ResponseEntity<Webhook> create(@RequestBody Map<String, String> body) {
    return ResponseEntity.ok(webhookService.createWebhook(
        body.get("url"), body.get("eventTypes"), body.get("secret")));
  }

  @DeleteMapping("/{id}")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Remove webhook")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    webhookService.deleteWebhook(id);
    return ResponseEntity.noContent().build();
  }
}
