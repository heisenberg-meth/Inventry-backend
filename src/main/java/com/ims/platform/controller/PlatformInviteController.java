package com.ims.platform.controller;

import com.ims.model.PlatformInvite;
import com.ims.platform.service.PlatformInviteService;
import com.ims.shared.rbac.RequiresRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/platform/invites")
@RequiredArgsConstructor
@Tag(name = "Platform - Invites", description = "Invite mechanism for platform administrators")
public class PlatformInviteController {

  private final PlatformInviteService inviteService;

  @PostMapping
  @RequiresRole("ROOT")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Create platform admin invite")
  public ResponseEntity<PlatformInvite> create(@RequestBody Map<String, String> body) {
    return ResponseEntity.ok(inviteService.createInvite(body.get("email"), body.get("role")));
  }

  @GetMapping
  @RequiresRole("ROOT")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "List all platform invites")
  public ResponseEntity<List<PlatformInvite>> list() {
    return ResponseEntity.ok(inviteService.getAllInvites());
  }

  @DeleteMapping("/{id}")
  @RequiresRole("ROOT")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Revoke invite")
  public ResponseEntity<Void> revoke(@PathVariable Long id) {
    inviteService.revokeInvite(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/accept")
  @Operation(summary = "Validate invite token")
  public ResponseEntity<PlatformInvite> accept(@RequestParam String token) {
    return ResponseEntity.ok(inviteService.validateToken(token));
  }

  @PostMapping("/complete")
  @Operation(summary = "Complete invite and set password")
  public ResponseEntity<Map<String, String>> complete(@RequestBody Map<String, String> body) {
    inviteService.completeInvite(body.get("token"), body.get("password"), body.get("name"));
    return ResponseEntity.ok(Map.of("message", "Account activated successfully"));
  }
}
