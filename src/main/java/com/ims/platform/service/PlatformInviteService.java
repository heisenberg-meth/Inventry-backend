package com.ims.platform.service;

import com.ims.model.PlatformInvite;
import com.ims.model.User;
import com.ims.model.UserRole;
import com.ims.platform.repository.PlatformInviteRepository;
import com.ims.shared.auth.JwtAuthDetails;
import com.ims.tenant.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformInviteService {

  private static final int INVITE_TTL_HOURS = 24;

  private final PlatformInviteRepository inviteRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Transactional
  public @NonNull PlatformInvite createInvite(@NonNull String email, @NonNull String role) {
    Objects.requireNonNull(email, "email required");
    Objects.requireNonNull(role, "role required");
    if (userRepository.findByEmailGlobal(email).isPresent()) {
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

    Long currentUserId = Objects.requireNonNull(extractUserId());
    String token = UUID.randomUUID().toString();

    PlatformInvite invite =
        Objects.requireNonNull(
            PlatformInvite.builder()
            .email(email)
            .role(UserRole.valueOf(role))
            .token(Objects.requireNonNull(token))
            .expiresAt(Objects.requireNonNull(LocalDateTime.now().plusHours(INVITE_TTL_HOURS)))
            .createdBy(currentUserId)
            .build());

    log.info("Platform invite created for {} by {}", email, currentUserId);
    return Objects.requireNonNull(inviteRepository.save(invite));
  }

  public @NonNull PlatformInvite validateToken(@NonNull String token) {
    Objects.requireNonNull(token, "token required");
    PlatformInvite tmpInvite =
        inviteRepository
            .findByToken(token)
            .orElseThrow(() -> new IllegalArgumentException("Invalid invite token"));
    PlatformInvite invite = Objects.requireNonNull(tmpInvite);

    if (invite.isExpired()) {
      throw new IllegalArgumentException("Invite token has expired");
    }

    if (invite.isUsed()) {
      throw new IllegalArgumentException("Invite has already been used");
    }

    return invite;
  }

  @Transactional
  public void completeInvite(
      @NonNull String token, @NonNull String password, @NonNull String name) {
    Objects.requireNonNull(token, "token required");
    Objects.requireNonNull(password, "password required");
    Objects.requireNonNull(name, "name required");
    PlatformInvite invite = validateToken(token);

    User user =
        Objects.requireNonNull(
            User.builder()
                .name(name)
                .email(invite.getEmail())
                .passwordHash(Objects.requireNonNull(passwordEncoder.encode(password)))
                .role(invite.getRole())
                .scope("PLATFORM")
                .isPlatformUser(true)
                .isActive(true)
                .isVerified(true)
                .build());
 
    userRepository.save(user);

    invite.setUsedAt(Objects.requireNonNull(LocalDateTime.now()));
    inviteRepository.save(invite);

    log.info("Platform invite completed for {}. User created.", invite.getEmail());
  }

  public List<PlatformInvite> getAllInvites() {
    return inviteRepository.findAll();
  }

  @Transactional
  public void revokeInvite(@NonNull Long id) {
    Objects.requireNonNull(id, "invite id required");
    inviteRepository.deleteById(id);
    log.info("Platform invite revoked: {}", id);
  }

  private @NonNull Long extractUserId() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getDetails() instanceof JwtAuthDetails details) {
      return Objects.requireNonNull(details.getUserId());
    }
    return Objects.requireNonNull(0L);
  }
}
