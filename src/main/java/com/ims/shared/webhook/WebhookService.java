package com.ims.shared.webhook;

import com.ims.model.Webhook;
import com.ims.model.WebhookOutbox;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.exception.BadRequestException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

  // RFC 1918 / RFC 3927 / RFC 4193 / RFC 4291 address-range constants used by isPrivateIp()
  private static final int IPV4_OCTETS = 4;
  private static final int IPV6_OCTETS = 16;
  private static final int UNSIGNED_BYTE_MASK = 0xFF;
  private static final int IPV4_LOOPBACK_FIRST_OCTET = 127;
  private static final int IPV4_PRIVATE_10_FIRST_OCTET = 10;
  private static final int IPV4_PRIVATE_172_FIRST_OCTET = 172;
  private static final int IPV4_PRIVATE_172_SECOND_OCTET_MIN = 16;
  private static final int IPV4_PRIVATE_172_SECOND_OCTET_MAX = 31;
  private static final int IPV4_PRIVATE_192_FIRST_OCTET = 192;
  private static final int IPV4_PRIVATE_192_SECOND_OCTET = 168;
  private static final int IPV4_LINK_LOCAL_FIRST_OCTET = 169;
  private static final int IPV4_LINK_LOCAL_SECOND_OCTET = 254;
  private static final int IPV6_UNIQUE_LOCAL_PREFIX_MASK = 0xFE;
  private static final int IPV6_UNIQUE_LOCAL_PREFIX = 0xFC;
  private static final byte IPV6_LINK_LOCAL_FIRST_BYTE = (byte) 0xFE;
  private static final int IPV6_LINK_LOCAL_SECOND_BYTE_MASK = 0xC0;
  private static final int IPV6_LINK_LOCAL_SECOND_BYTE_VALUE = 0x80;

  private final WebhookRepository webhookRepository;
  private final WebhookOutboxRepository outboxRepository;
  private final RestTemplate webhookRestTemplate;
  private final com.ims.shared.security.SecretEncryptionService encryptionService;
  private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
  private final com.ims.shared.metrics.BusinessMetrics businessMetrics;

  public List<Webhook> getMyWebhooks() {
    return webhookRepository.findByTenantId(TenantContext.requireTenantId());
  }

  @Transactional
  public Webhook createWebhook(String url, String eventTypes, String secret) {
    URI normalizedUri = validateAndNormalize(url);

    Webhook webhook =
        Objects.requireNonNull(
            Webhook.builder()
                .tenantId(TenantContext.requireTenantId())
                .url(Objects.requireNonNull(normalizedUri.toString()))
                .eventTypes(Objects.requireNonNull(eventTypes))
                .secret(Objects.requireNonNull(encryptionService.encrypt(secret)))
                .isActive(true)
                .build());
    return webhookRepository.save(webhook);
  }

  @Transactional
  public void deleteWebhook(Long id) {
    webhookRepository
        .findById(id)
        .ifPresent(
            w -> {
              if (w.getTenantId().equals(TenantContext.getTenantId())) {
                webhookRepository.delete(w);
              }
            });
  }

  /**
   * Dispatches an event by saving it to the transactional outbox.
   * This ensures the event is persisted along with the business change.
   */
  @Transactional
  public void dispatch(Long tenantId, String eventType, Object payload) {
    try {
      String jsonPayload = objectMapper.writeValueAsString(payload);
      
      WebhookOutbox entry = WebhookOutbox.builder()
          .tenantId(tenantId)
          .eventType(eventType)
          .payload(jsonPayload)
          .status(WebhookOutbox.OutboxStatus.PENDING)
          .build();
          
      outboxRepository.save(entry);
      businessMetrics.incrementWebhookEvents();
      log.debug("Saved webhook event {} to outbox for tenant {}", eventType, tenantId);
    } catch (Exception e) {
      log.error("Failed to save webhook to outbox: {}", e.getMessage());
      // We don't throw here to avoid failing the main transaction if webhook persistence fails,
      // but in a strict system, you might want to throw to ensure consistency.
    }
  }

  /**
   * Processes a single outbox entry. Called by the background worker.
   */
  @Transactional
  public void processOutboxEntry(WebhookOutbox entry) {
    List<Webhook> webhooks = webhookRepository.findByTenantId(entry.getTenantId());
    boolean atLeastOneSent = false;

    for (Webhook webhook : webhooks) {
      if (Boolean.TRUE.equals(webhook.getIsActive())
          && webhook.getEventTypes().contains(entry.getEventType())) {
        try {
          this.sendWebhook(webhook, entry.getEventType(), entry.getTenantId(), entry.getPayload());
          atLeastOneSent = true;
        } catch (Exception e) {
          log.error("Failed to send webhook {} for outbox entry {}: {}", webhook.getId(), entry.getId(), e.getMessage());
        }
      }
    }

    if (atLeastOneSent || webhooks.isEmpty()) {
      entry.setStatus(WebhookOutbox.OutboxStatus.COMPLETED);
      entry.setProcessedAt(LocalDateTime.now());
    } else {
      entry.setStatus(WebhookOutbox.OutboxStatus.FAILED);
      entry.setRetryCount(entry.getRetryCount() + 1);
      entry.setNextRetryAt(LocalDateTime.now().plusMinutes((long) Math.pow(2, entry.getRetryCount())));
    }
    outboxRepository.save(entry);
  }

  @Retry(name = "webhookRetry")
  @CircuitBreaker(name = "webhook")
  public void sendWebhook(Webhook webhook, String eventType, Long tenantId, Object payload) {
    try {
      validateAndNormalize(webhook.getUrl());

      String timestamp = LocalDateTime.now().toString();
      Map<String, Object> body =
          Map.of(
              "version", "v1",
              "event", eventType,
              "tenant_id", tenantId,
              "timestamp", timestamp,
              "data", payload);

      String jsonPayload = objectMapper.writeValueAsString(body);
      String decryptedSecret = encryptionService.decrypt(webhook.getSecret());

      org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
      headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
      headers.set("X-IMS-Signature", com.ims.shared.util.CryptoUtils.hmacSha256(jsonPayload, decryptedSecret));
      headers.set("X-IMS-Timestamp", timestamp);

      org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(jsonPayload, headers);

      webhookRestTemplate.postForEntity(
          Objects.requireNonNull(webhook.getUrl()), entity, String.class);
      log.debug("Webhook dispatched to {} for event {}", webhook.getUrl(), eventType);
    } catch (Exception e) {
      log.error("Failed to dispatch webhook to {}: {}", webhook.getUrl(), e.getMessage());
      throw new RuntimeException("Webhook dispatch failed", e); // Rethrow for retry
    }
  }

  public void recover(
      Webhook webhook, String eventType, Long tenantId, Object payload, Exception ex) {
    log.error(
        "Permanent failure delivering webhook to {} after retries: {}",
        webhook.getUrl(),
        ex.getMessage());
    // Potentially mark webhook as inactive or log to a dead-letter table
  }

  public URI validateAndNormalize(String url) {
    if (url == null || url.isBlank()) {
      throw new BadRequestException("URL cannot be empty");
    }

    URI uri;
    try {
      uri = URI.create(url.trim());
    } catch (Exception e) {
      throw new BadRequestException("Invalid URL format");
    }

    if (!List.of("http", "https").contains(uri.getScheme())) {
      throw new BadRequestException("Only http/https allowed");
    }

    if (uri.getUserInfo() != null) {
      throw new BadRequestException("Credentials in URL are not allowed");
    }

    InetAddress addr;
    try {
      addr = InetAddress.getByName(uri.getHost());
    } catch (UnknownHostException e) {
      throw new BadRequestException("Invalid host: " + uri.getHost());
    }

    if (addr.isAnyLocalAddress()
        || addr.isLoopbackAddress()
        || addr.isLinkLocalAddress()
        || addr.isSiteLocalAddress()
        || isPrivateIp(addr)) {
      throw new BadRequestException(
          "Private/internal addresses are not allowed: " + addr.getHostAddress());
    }

    return uri;
  }

  private boolean isPrivateIp(InetAddress addr) {
    byte[] ip = addr.getAddress();
    if (ip.length == IPV4_OCTETS) {
      return isPrivateIpv4(ip);
    }
    if (ip.length == IPV6_OCTETS) {
      return isPrivateIpv6(addr, ip);
    }
    return false;
  }

  private boolean isPrivateIpv4(byte[] ip) {
    int first = ip[0] & UNSIGNED_BYTE_MASK;
    int second = ip[1] & UNSIGNED_BYTE_MASK;
    // 127.0.0.0/8 loopback
    if (first == IPV4_LOOPBACK_FIRST_OCTET) {
      return true;
    }
    // 10.0.0.0/8 private
    if (first == IPV4_PRIVATE_10_FIRST_OCTET) {
      return true;
    }
    // 172.16.0.0/12 private
    if (first == IPV4_PRIVATE_172_FIRST_OCTET
        && second >= IPV4_PRIVATE_172_SECOND_OCTET_MIN
        && second <= IPV4_PRIVATE_172_SECOND_OCTET_MAX) {
      return true;
    }
    // 192.168.0.0/16 private
    if (first == IPV4_PRIVATE_192_FIRST_OCTET && second == IPV4_PRIVATE_192_SECOND_OCTET) {
      return true;
    }
    // 169.254.0.0/16 link-local
    return first == IPV4_LINK_LOCAL_FIRST_OCTET && second == IPV4_LINK_LOCAL_SECOND_OCTET;
  }

  private boolean isPrivateIpv6(InetAddress addr, byte[] ip) {
    if (addr.isLoopbackAddress()) {
      return true;
    }
    // fc00::/7 unique local
    if ((ip[0] & IPV6_UNIQUE_LOCAL_PREFIX_MASK) == IPV6_UNIQUE_LOCAL_PREFIX) {
      return true;
    }
    // fe80::/10 link-local
    return ip[0] == IPV6_LINK_LOCAL_FIRST_BYTE
        && (ip[1] & IPV6_LINK_LOCAL_SECOND_BYTE_MASK) == IPV6_LINK_LOCAL_SECOND_BYTE_VALUE;
  }
}
