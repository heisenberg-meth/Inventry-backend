package com.ims.shared.webhook;

import com.ims.model.WebhookOutbox;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class WebhookOutboxWorker {

    private final WebhookOutboxRepository outboxRepository;
    private final WebhookService webhookService;

    /**
     * Processes pending outbox entries every 10 seconds.
     * Uses ShedLock to prevent multiple instances from processing the same entries in a cluster.
     */
    @Scheduled(fixedDelay = 10000)
    @SchedulerLock(name = "processWebhookOutbox", lockAtMostFor = "1m", lockAtLeastFor = "5s")
    public void processOutbox() {
        log.trace("Polling webhook outbox for pending entries...");
        
        List<WebhookOutbox> pendingEntries = outboxRepository.findPendingForProcessing(
            LocalDateTime.now(), 
            PageRequest.of(0, 100)
        );

        if (pendingEntries.isEmpty()) {
            return;
        }

        log.info("Found {} pending webhook events to process", pendingEntries.size());

        for (WebhookOutbox entry : pendingEntries) {
            try {
                // Mark as processing to avoid concurrent pick-up if ShedLock was somehow bypassed or for visibility
                entry.setStatus(WebhookOutbox.OutboxStatus.PROCESSING);
                outboxRepository.save(entry);
                
                webhookService.processOutboxEntry(entry);
            } catch (Exception e) {
                log.error("Critical failure processing outbox entry {}: {}", entry.getId(), e.getMessage());
                entry.setStatus(WebhookOutbox.OutboxStatus.FAILED);
                entry.setLastError(e.getMessage());
                outboxRepository.save(entry);
            }
        }
    }
}
