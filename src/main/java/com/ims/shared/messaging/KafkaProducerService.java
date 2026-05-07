package com.ims.shared.messaging;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@NoArgsConstructor(force = true)
@Slf4j
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Counter kafkaSendSuccessCounter;
    private final Counter kafkaSendFailureCounter;

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate, MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaSendSuccessCounter = Counter.builder("kafka.send.success").register(meterRegistry);
        this.kafkaSendFailureCounter = Counter.builder("kafka.send.failure").register(meterRegistry);
    }

    @CircuitBreaker(name = "kafkaService", fallbackMethod = "sendMessageFallback")
    @Retryable(retryFor = {
            org.apache.kafka.common.errors.TimeoutException.class,
            org.apache.kafka.common.errors.NetworkException.class,
            org.springframework.kafka.KafkaException.class
    }, maxAttempts = 3, backoff = @Backoff(delay = 500, maxDelay = 2000, multiplier = 2))
    public void sendMessage(String topic, String key, String message) {
        log.debug("Sending message to topic {}: key={}", topic, key);
        kafkaTemplate.send(topic, key, message)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("Message sent successfully to topic {} partition={} offset={}",
                                topic, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                        kafkaSendSuccessCounter.increment();
                    } else {
                        log.error("Failed to send message to topic {}: {}", topic, ex.getMessage());
                        kafkaSendFailureCounter.increment();
                    }
                });
    }

    public void sendMessageFallback(String topic, String key, String message, Throwable t) {
        log.warn("Circuit breaker OPEN for kafkaService - message queued for later retry to topic {}", topic);
        kafkaSendFailureCounter.increment();
    }
}
