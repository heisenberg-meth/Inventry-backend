package com.ims.platform.controller;

import com.ims.dto.request.CompleteInviteRequest;
import com.ims.dto.request.CreateInviteRequest;
import com.ims.model.PlatformInvite;
import com.ims.platform.service.PlatformInviteService;
import com.ims.shared.rbac.RequiresRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/invites")
@RequiredArgsConstructor
@Tag(name = "Platform - Invites", description = "Invite mechanism for platform administrators")
public class PlatformInviteController {

  private final PlatformInviteService inviteService;

  @PostMapping
  @RequiresRole("ROOT")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Create platform admin invite")
  public ResponseEntity<PlatformInvite> create(
      @Valid @RequestBody
          CreateInviteRequest request) {
    Objects.requireNonNull(request, "request required");
    String email = Objects.requireNonNull(request.email());
    String roleName = Objects.requireNonNull(request.role().name());
    return ResponseEntity.ok(inviteService.createInvite(email, roleName));
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
    inviteService.revokeInvite(Objects.requireNonNull(id));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/accept")
  @Operation(summary = "Validate invite token")
  public ResponseEntity<PlatformInvite> accept(@RequestParam String token) {
    return ResponseEntity.ok(inviteService.validateToken(Objects.requireNonNull(token)));
  }

  @PostMapping("/complete")
  @Operation(summary = "Complete invite and set password")
  public ResponseEntity<Map<String, String>> complete(
      @Valid @RequestBody
          CompleteInviteRequest request) {
    Objects.requireNonNull(request, "request required");
    String token = Objects.requireNonNull(request.token());
    String password = Objects.requireNonNull(request.password());
    String name = Objects.requireNonNull(request.name());
    inviteService.completeInvite(token, password, name);
    return ResponseEntity.ok(Map.of("message", "Account activated successfully"));
  }
}
