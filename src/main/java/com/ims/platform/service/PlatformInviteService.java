package com.ims.platform.service;

import com.ims.model.PlatformInvite;
import com.ims.model.User;
import com.ims.model.UserRole;
import com.ims.platform.repository.PlatformInviteRepository;
import com.ims.shared.auth.JwtAuthDetails;
import com.ims.tenant.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformInviteService {

  /** Platform invites are valid for 24 hours from creation. */
  private static final int INVITE_TTL_HOURS = 24;

  private final PlatformInviteRepository inviteRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Transactional
  public PlatformInvite createInvite(String email, String role) {
    if (userRepository.findByEmailUnfiltered(email).isPresent()) {
      throw new IllegalArgumentException("User with this email already exists");
    }

    inviteRepository
        .findByEmail(email)
        .ifPresent(
            invite -> {
              if (!invite.isExpired() && !invite.isUsed()) {
                throw new IllegalArgumentException(
                    "A pending invite already exists for this email");
              }
              inviteRepository.delete(invite);
            });

    Long currentUserId = extractUserId();
    String token = UUID.randomUUID().toString();

    PlatformInvite invite =
        PlatformInvite.builder()
            .email(email)
            .role(UserRole.valueOf(role))
            .token(token)
            .expiresAt(LocalDateTime.now().plusHours(INVITE_TTL_HOURS))
            .createdBy(currentUserId)
            .build();

    log.info("Platform invite created for {} by {}", email, currentUserId);
    return java.util.Objects.requireNonNull(inviteRepository.save(invite));
  }

  public PlatformInvite validateToken(String token) {
    PlatformInvite invite =
        inviteRepository
            .findByToken(token)
            .orElseThrow(() -> new IllegalArgumentException("Invalid invite token"));

    if (invite.isExpired()) {
      throw new IllegalArgumentException("Invite token has expired");
    }

    if (invite.isUsed()) {
      throw new IllegalArgumentException("Invite has already been used");
    }

    return invite;
  }

  @Transactional
  public void completeInvite(String token, String password, String name) {
    PlatformInvite invite = validateToken(token);

    User user =
        User.builder()
            .name(name)
            .email(invite.getEmail())
            .passwordHash(passwordEncoder.encode(password))
            .role(invite.getRole())
            .scope("PLATFORM")
            .isPlatformUser(true)
            .isActive(true)
            .isVerified(true)
            .build();

    userRepository.save(user);

    invite.setUsedAt(LocalDateTime.now());
    inviteRepository.save(invite);

    log.info("Platform invite completed for {}. User created.", invite.getEmail());
  }

  public List<PlatformInvite> getAllInvites() {
    return inviteRepository.findAll();
  }

  @Transactional
  public void revokeInvite(Long id) {
    inviteRepository.deleteById(id);
    log.info("Platform invite revoked: {}", id);
  }

  private Long extractUserId() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getDetails() instanceof JwtAuthDetails details) {
      return details.getUserId();
    }
    return 0L; // Fallback for system operations
  }
}
