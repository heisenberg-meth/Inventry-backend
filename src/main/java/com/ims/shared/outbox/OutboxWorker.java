package com.ims.shared.outbox;

import com.ims.shared.messaging.KafkaProducerService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@NoArgsConstructor(force = true)
@Slf4j
public class OutboxWorker {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaProducerService kafkaProducerService;

    @Scheduled(fixedDelay = 5000) // Every 5 seconds
    @Transactional
    public void processOutbox() {
        List<OutboxEvent> events = outboxEventRepository.findPendingEvents();
        if (events.isEmpty()) {
            return;
        }

        log.debug("Processing {} pending outbox events", events.size());

        for (OutboxEvent event : events) {
            try {
                String topic = "ims." + event.getAggregateType().toLowerCase() + "." + event.getType().toLowerCase();
                kafkaProducerService.sendMessage(topic, event.getAggregateId(), event.getPayload());
                
                event.setStatus("SENT");
                event.setProcessedAt(LocalDateTime.now());
            } catch (Exception e) {
                int retries = event.getRetryCount() + 1;
                event.setRetryCount(retries);
                log.error("Failed to process outbox event: {} (Attempt {})", event.getId(), retries, e);
                
                if (retries >= 3) {
                    event.setStatus("FAILED");
                }
                event.setErrorMessage(e.getMessage());
            }
        }
        
        outboxEventRepository.saveAll(events);
    }
}
