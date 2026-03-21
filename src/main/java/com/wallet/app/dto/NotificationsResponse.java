package com.wallet.app.dto;

import java.util.List;

public record NotificationsResponse(
    long unreadCount,
    List<NotificationResponse> notifications
) {
}
