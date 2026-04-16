package com.ims.shared.webhook;

import com.ims.model.Webhook;
import com.ims.shared.auth.TenantContext;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class WebhookService {

  private final WebhookRepository webhookRepository;
  private final RestTemplate restTemplate = new RestTemplate();

  public List<Webhook> getMyWebhooks() {
    return webhookRepository.findByTenantId(TenantContext.get());
  }

  @Transactional
  public Webhook createWebhook(String url, String eventTypes, String secret) {
    Webhook webhook = Webhook.builder()
        .tenantId(TenantContext.get())
        .url(url)
        .eventTypes(eventTypes)
        .secret(secret)
        .isActive(true)
        .build();
    return webhookRepository.save(webhook);
  }

  @Transactional
  public void deleteWebhook(Long id) {
    webhookRepository.findById(id).ifPresent(w -> {
      if (w.getTenantId().equals(TenantContext.get())) {
        webhookRepository.delete(w);
      }
    });
  }

  @Async
  public void dispatch(Long tenantId, String eventType, Object payload) {
    List<Webhook> webhooks = webhookRepository.findByTenantId(tenantId);
    
    for (Webhook webhook : webhooks) {
      if (Boolean.TRUE.equals(webhook.getIsActive()) && webhook.getEventTypes().contains(eventType)) {
        try {
          Map<String, Object> body = Map.of(
              "event", eventType,
              "tenant_id", tenantId,
              "timestamp", java.time.LocalDateTime.now().toString(),
              "data", payload
          );
          restTemplate.postForEntity(webhook.getUrl(), body, String.class);
          log.debug("Webhook dispatched to {} for event {}", webhook.getUrl(), eventType);
        } catch (Exception e) {
          log.error("Failed to dispatch webhook to {}: {}", webhook.getUrl(), e.getMessage());
        }
      }
    }
  }
}
