package com.ims.platform.service;

import com.ims.model.Subscription;
import com.ims.model.SubscriptionStatus;
import com.ims.platform.repository.SubscriptionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

  private final SubscriptionRepository subscriptionRepository;

  public List<Subscription> getActiveSubscriptions() {
    return subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE);
  }

  @Transactional
  public Subscription extendSubscription(Long id, int days) {
    Objects.requireNonNull(id, "subscription id required");
    Subscription tmpSubscription = subscriptionRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Subscription not found"));
    Subscription subscription = Objects.requireNonNull(tmpSubscription);

    subscription.setEndDate(Objects.requireNonNull(Objects.requireNonNull(subscription.getEndDate()).plusDays(days)));
    subscription.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

    log.info("Subscription {} extended by {} days", id, days);
    Subscription tmpSaved = subscriptionRepository.save(subscription);
    return Objects.requireNonNull(tmpSaved);
  }
}
