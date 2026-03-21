package com.wallet.app.dto;

import java.time.OffsetDateTime;

public record UserPreferencesResponse(
    boolean emailNotifications,
    boolean transactionNotifications,
    boolean securityAlerts,
    OffsetDateTime updatedAt
) {
}
