package com.ims.tenant.controller;

import com.ims.model.Webhook;
import com.ims.shared.rbac.RequiresRole;
import com.ims.shared.webhook.WebhookService;
import com.ims.tenant.dto.CreateWebhookRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/settings/webhooks")
@RequiredArgsConstructor
@Tag(name = "Tenant - Webhooks", description = "Configure external webhook alerts")
@SecurityRequirement(name = "bearerAuth")
public class WebhookController {

  private final WebhookService webhookService;

  @GetMapping
  @RequiresRole({ "TENANT_ADMIN", "BUSINESS_MANAGER" })
  @Operation(summary = "List all configured webhooks")
  public ResponseEntity<List<Webhook>> list() {
    return ResponseEntity.ok(webhookService.getMyWebhooks());
  }

  @PostMapping
  @RequiresRole({ "TENANT_ADMIN", "BUSINESS_MANAGER" })
  @Operation(summary = "Create new webhook")
  public ResponseEntity<Webhook> create(@RequestBody @Valid CreateWebhookRequest req) {
    return ResponseEntity.ok(
        webhookService.createWebhook(req.getUrl(), req.getEventTypes(), req.getSecret()));
  }

  @DeleteMapping("/{id}")
  @RequiresRole({ "TENANT_ADMIN", "BUSINESS_MANAGER" })
  @Operation(summary = "Remove webhook")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    webhookService.deleteWebhook(Objects.requireNonNull(id));
    return ResponseEntity.noContent().build();
  }
}
