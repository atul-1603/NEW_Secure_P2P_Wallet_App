package com.wallet.app.service;

import java.util.List;
import java.util.UUID;

import com.wallet.app.dto.NotificationResponse;
import com.wallet.app.dto.NotificationsResponse;
import com.wallet.app.entity.NotificationType;

public interface NotificationService {

    NotificationsResponse getForCurrentUser(String username);

    NotificationResponse markAsRead(String username, UUID notificationId);

    List<NotificationResponse> markAllAsRead(String username);

    void deleteNotification(String username, UUID notificationId);

    NotificationResponse createAndPublish(UUID userId, NotificationType type, String title, String message);
}
