package com.ims.platform.controller;

import com.ims.dto.CreatePlatformUserRequest;
import com.ims.model.User;
import com.ims.platform.service.PlatformUserService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/users")
@RequiredArgsConstructor
public class PlatformUserController {

  private final PlatformUserService platformUserService;

  @PostMapping
  @PreAuthorize("hasAuthority('ROLE_ROOT')")
  @ResponseStatus(HttpStatus.CREATED)
  public User createPlatformUser(@Valid @RequestBody CreatePlatformUserRequest request) {
    return platformUserService.createPlatformUser(request);
  }

  @GetMapping
  @PreAuthorize("hasAnyAuthority('ROLE_ROOT', 'ROLE_PLATFORM_ADMIN')")
  public Page<User> getPlatformUsers(Pageable pageable) {
    return platformUserService.getPlatformUsers(pageable);
  }

  @PatchMapping("/{id}/role")
  @PreAuthorize("hasAuthority('ROLE_ROOT')")
  public User updateRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
    String role = body.get("role");
    if (role == null) {
      throw new IllegalArgumentException("Role is required");
    }
    return platformUserService.updatePlatformUserRole(id, role);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('ROLE_ROOT')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivatePlatformUser(@PathVariable Long id) {
    platformUserService.deactivatePlatformUser(id);
  }
}
