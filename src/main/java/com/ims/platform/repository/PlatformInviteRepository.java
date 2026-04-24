package com.ims.platform.repository;

import com.ims.model.PlatformInvite;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformInviteRepository extends JpaRepository<PlatformInvite, Long> {
  Optional<PlatformInvite> findByToken(String token);

  Optional<PlatformInvite> findByEmail(String email);
}
