package com.wallet.app.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    String type,
    String title,
    String message,
    boolean isRead,
    OffsetDateTime createdAt
) {
}
