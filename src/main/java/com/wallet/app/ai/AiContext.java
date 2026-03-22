package com.wallet.app.ai;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AiContext(
    UUID userId,
    String username,
    String fullName,
    String currentPage,
    BigDecimal balance,
    String currency,
    String walletStatus,
    List<AiTransactionContext> recentTransactions,
    AiNotificationSummary notifications
) {

    public record AiTransactionContext(
        String direction,
        BigDecimal amount,
        String currency,
        String type,
        String status,
        String reference,
        String counterpartyWalletMasked,
        String createdAt
    ) {
    }

    public record AiNotificationSummary(
        long unreadCount,
        List<String> recentTitles,
        List<String> recentTypes
    ) {
    }
}
