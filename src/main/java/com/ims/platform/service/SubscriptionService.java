package com.ims.platform.service;

import com.ims.model.Subscription;
import com.ims.platform.repository.SubscriptionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
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
        return subscriptionRepository.findByStatus("ACTIVE");
    }

    @Transactional
    public Subscription extendSubscription(Long id, int days) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Subscription not found"));
        
        subscription.setEndDate(subscription.getEndDate().plusDays(days));
        subscription.setUpdatedAt(LocalDateTime.now());
        
        log.info("Subscription {} extended by {} days", id, days);
        return subscriptionRepository.save(subscription);
    }
}
