package com.ims.shared.auth;

import com.ims.model.EmailVerification;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
  Optional<EmailVerification> findByToken(String token);
  void deleteByUserId(Long userId);
}
