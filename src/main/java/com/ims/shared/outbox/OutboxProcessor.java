package com.ims.shared.outbox;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxProcessor {

  private final OutboxRepository outboxRepository;
  private final OutboxEventHandlerRegistry handlerRegistry;

  /**
   * Polls the outbox table for pending events every 5 seconds.
   * Uses a small batch size to prevent long-running transactions.
   */
  @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")
  public void processOutbox() {
    var pendingEvents = outboxRepository.findPendingEvents(PageRequest.of(0, 20));
    
    if (pendingEvents.isEmpty()) {
      return;
    }

    log.debug("Processing {} outbox events", pendingEvents.size());
    for (OutboxEvent event : pendingEvents) {
      processEvent(Objects.requireNonNull(event));
    }
  }

  private void processEvent(OutboxEvent event) {
    try {
      handlerRegistry.getHandler(event.getEventType())
          .ifPresentOrElse(
              handler -> {
                handler.handle(event);
                markAsProcessed(event);
              },
              () -> {
                log.warn("No handler found for event type: {}", event.getEventType());
                markAsFailed(event, "No handler found");
              });
    } catch (Exception e) {
      log.error("Error processing outbox event {}: {}", event.getId(), e.getMessage());
      markAsFailed(event, e.getMessage());
    }
  }

  @Transactional
  protected void markAsProcessed(OutboxEvent event) {
    event.setProcessed(true);
    event.setProcessedAt(java.time.LocalDateTime.now());
    outboxRepository.save(event);
  }

  @Transactional
  protected void markAsFailed(OutboxEvent event, String error) {
    event.setAttempts(event.getAttempts() + 1);
    event.setLastError(error);
    outboxRepository.save(event);
  }
}
