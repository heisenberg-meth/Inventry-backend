package com.ims.platform.controller;

import com.ims.model.PlatformInvite;
import com.ims.platform.service.PlatformInviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/invites")
@RequiredArgsConstructor
@Tag(name = "Platform - Invites", description = "Invite mechanism for platform administrators")
public class PlatformInviteController {

  private final PlatformInviteService inviteService;

  @PostMapping
  @PreAuthorize("hasRole('ROOT')")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Create platform admin invite")
  public ResponseEntity<PlatformInvite> create(@RequestBody Map<String, String> body) {
    return ResponseEntity.ok(inviteService.createInvite(body.get("email"), body.get("role")));
  }

  @GetMapping
  @PreAuthorize("hasRole('ROOT')")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "List all platform invites")
  public ResponseEntity<List<PlatformInvite>> list() {
    return ResponseEntity.ok(inviteService.getAllInvites());
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ROOT')")
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
