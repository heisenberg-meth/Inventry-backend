package com.ims.shared.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {

  private final OutboxRepository outboxRepository;
  private final ObjectMapper objectMapper;

  @Transactional(propagation = Propagation.MANDATORY)
  public void saveEvent(String aggregateType, String aggregateId, String eventType, Object payload) {
    try {
      String jsonPayload = objectMapper.writeValueAsString(payload);
      OutboxEvent event = OutboxEvent.builder()
          .aggregateType(aggregateType)
          .aggregateId(aggregateId)
          .eventType(eventType)
          .payload(jsonPayload)
          .createdAt(LocalDateTime.now())
          .processed(false)
          .build();
      
      outboxRepository.save(event);
      log.debug("Saved outbox event: {} for {}/{}", eventType, aggregateType, aggregateId);
    } catch (Exception e) {
      log.error("Failed to save outbox event", e);
      throw new RuntimeException("Outbox save failed", e);
    }
  }
}
