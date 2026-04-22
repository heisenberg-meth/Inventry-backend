package com.ims.shared.webhook;

import com.ims.model.Webhook;
import com.ims.shared.auth.TenantContext;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import com.ims.shared.exception.BadRequestException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

  private final WebhookRepository webhookRepository;
  private final RestTemplate webhookRestTemplate;

  public List<Webhook> getMyWebhooks() {
    return webhookRepository.findByTenantId(TenantContext.getTenantId());
  }

  @Transactional
  public Webhook createWebhook(String url, String eventTypes, String secret) {
    URI normalizedUri = validateAndNormalize(url);
    
    Webhook webhook = Webhook.builder()
        .tenantId(TenantContext.getTenantId())
        .url(normalizedUri.toString())
        .eventTypes(eventTypes)
        .secret(secret)
        .isActive(true)
        .build();
    return webhookRepository.save(webhook);
  }

  @Transactional
  public void deleteWebhook(Long id) {
    webhookRepository.findById(id).ifPresent(w -> {
      if (w.getTenantId().equals(TenantContext.getTenantId())) {
        webhookRepository.delete(w);
      }
    });
  }

  public void dispatch(Long tenantId, String eventType, Object payload) {
    List<Webhook> webhooks = webhookRepository.findByTenantId(tenantId);

    for (Webhook webhook : webhooks) {
      if (Boolean.TRUE.equals(webhook.getIsActive()) && webhook.getEventTypes().contains(eventType)) {
        this.sendWebhook(webhook, eventType, tenantId, payload);
      }
    }
  }

  @Async("webhookExecutor")
  @Retryable(
      retryFor = {Exception.class},
      maxAttempts = 3,
      backoff = @Backoff(delay = 1000, multiplier = 2))
  @CircuitBreaker(name = "webhook")
  public void sendWebhook(Webhook webhook, String eventType, Long tenantId, Object payload) {
    try {
      validateAndNormalize(webhook.getUrl());
      Map<String, Object> body =
          Map.of(
              "version", "v1",
              "event", eventType,
              "tenant_id", tenantId,
              "timestamp", java.time.LocalDateTime.now().toString(),
              "data", payload);
      webhookRestTemplate.postForEntity(webhook.getUrl(), body, String.class);
      log.debug("Webhook dispatched to {} for event {}", webhook.getUrl(), eventType);
    } catch (Exception e) {
      log.error("Failed to dispatch webhook to {}: {}", webhook.getUrl(), e.getMessage());
      throw e; // Rethrow for retry/circuit breaker
    }
  }

  @Recover
  public void recover(Exception ex, Webhook webhook, String eventType, Long tenantId, Object payload) {
    log.error("Permanent failure delivering webhook to {} after retries: {}", webhook.getUrl(), ex.getMessage());
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
      throw new BadRequestException("Private/internal addresses are not allowed: " + addr.getHostAddress());
    }

    return uri;
  }

  private boolean isPrivateIp(InetAddress addr) {
    byte[] ip = addr.getAddress();
    if (ip.length == 4) { // IPv4
      int first = ip[0] & 0xFF;
      int second = ip[1] & 0xFF;
      if (first == 127) return true; // 127.0.0.0/8
      if (first == 10) return true; // 10.0.0.0/8
      if (first == 172 && (second >= 16 && second <= 31)) return true; // 172.16.0.0/12
      if (first == 192 && second == 168) return true; // 192.168.0.0/16
      if (first == 169 && second == 254) return true; // 169.254.0.0/16
    } else if (ip.length == 16) { // IPv6
      if (addr.isLoopbackAddress()) return true;
      if ((ip[0] & 0xFE) == 0xFC) return true; // fc00::/7
      if (ip[0] == (byte) 0xFE && (ip[1] & 0xC0) == (byte) 0x80) return true; // fe80::/10
    }
    return false;
  }
}
