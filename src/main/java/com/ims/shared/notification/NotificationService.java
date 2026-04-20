package com.ims.shared.notification;

import com.ims.model.Notification;
import com.ims.shared.auth.TenantContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class NotificationService {

  private final NotificationRepository notificationRepository;

  public List<Notification> getMyNotifications(Long userId) {
    return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
  }

  public List<Notification> getUnreadNotifications(Long userId) {
    return notificationRepository.findByUserIdAndIsReadFalse(userId);
  }

  @Transactional
  public void markAsRead(Long id) {
    notificationRepository.findById(id).ifPresent(n -> {
      n.setIsRead(true);
      notificationRepository.save(n);
    });
  }

  @Transactional
  public void markAllAsRead(Long userId) {
    var unread = notificationRepository.findByUserIdAndIsReadFalse(userId);
    unread.forEach(n -> n.setIsRead(true));
    notificationRepository.saveAll(unread);
  }

  @Transactional
  public void createNotification(Long userId, String title, String message, String type, Long resourceId) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      log.error("Tenant ID is missing in NotificationService.createNotification");
      throw new IllegalStateException("Tenant context is missing");
    }
    log.info("TenantContext: {}", tenantId);
    var notification = Notification.builder()
        .userId(userId)
        .tenantId(TenantContext.getTenantId())
        .title(title)
        .message(message)
        .type(type)
        .resourceId(resourceId)
        .build();
    notificationRepository.save(notification);
    log.debug("Notification created for user {}: {}", userId, title);
  }
}
