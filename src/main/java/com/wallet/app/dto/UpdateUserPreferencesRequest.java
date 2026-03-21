package com.wallet.app.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateUserPreferencesRequest(
    @NotNull
    Boolean emailNotifications,

    @NotNull
    Boolean transactionNotifications,

    @NotNull
    Boolean securityAlerts
) {
}
