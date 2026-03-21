package com.wallet.app.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WithdrawalHistoryItem(
    UUID id,
    BigDecimal amount,
    String status,
    UUID bankAccountId,
    String bankName,
    String accountHolderName,
    String maskedAccountNumber,
    UUID referenceId,
    OffsetDateTime createdAt,
    OffsetDateTime processedAt
) {
}
