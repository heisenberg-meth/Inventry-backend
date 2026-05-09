package com.ims.shared.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "false")
@Slf4j
public class NoOpKafkaProducerService {

  private final Counter kafkaSendFailureCounter;

  public NoOpKafkaProducerService(MeterRegistry meterRegistry) {
    this.kafkaSendFailureCounter = Counter.builder("kafka.send.failure").register(meterRegistry);
  }

  public void sendMessage(String topic, String key, String message) {
    log.debug("Kafka disabled - skipping message to topic {}: key={}", topic, key);
  }

  public void sendMessageFallback(String topic, String key, String message, Throwable t) {
    log.debug("Kafka disabled - fallback for topic {}", topic);
  }
}
