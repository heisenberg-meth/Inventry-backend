package com.ims.shared.notification;

import com.ims.model.Notification;
import com.ims.shared.auth.TenantContext;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

  private final NotificationRepository notificationRepository;

  public List<Notification> getMyNotifications(@NonNull Long userId) {
    return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
  }

  public List<Notification> getUnreadNotifications(@NonNull Long userId) {
    return notificationRepository.findByUserIdAndIsReadFalse(userId);
  }

  @Transactional
  public void markAsRead(@NonNull Long id) {
    notificationRepository
        .findById(id)
        .ifPresent(
            n -> {
              n.setIsRead(true);
              notificationRepository.save(n);
            });
  }

  @Transactional
  public void markAllAsRead(@NonNull Long userId) {
    var unread = notificationRepository.findByUserIdAndIsReadFalse(userId);
    unread.forEach(n -> n.setIsRead(true));
    notificationRepository.saveAll(unread);
  }

  @Transactional
  public void createNotification(
      Long tenantId, Long userId, String title, String message, String type, Long resourceId) {
    if (tenantId == null || tenantId <= 0) {
      throw new IllegalArgumentException("tenantId is required and must be positive");
    }

    Notification notification =
        Objects.requireNonNull(
            Notification.builder()
                .userId(userId)
                .tenantId(tenantId)
                .title(title)
                .message(message)
                .type(type)
                .resourceId(resourceId)
                .build());
    notificationRepository.save(notification);
    log.debug("Notification created for tenant {} user {}: {}", tenantId, userId, title);
  }

  @Transactional
  public void createNotificationForCurrentTenant(
      Long userId, String title, String message, String type, Long resourceId) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      log.warn("Tenant context missing in createNotificationForCurrentTenant");
      throw new com.ims.shared.exception.TenantContextException("Tenant context missing");
    }
    createNotification(tenantId, userId, title, message, type, resourceId);
  }
}
