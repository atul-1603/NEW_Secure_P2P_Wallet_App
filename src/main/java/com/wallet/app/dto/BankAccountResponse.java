package com.wallet.app.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BankAccountResponse(
    UUID id,
    String accountHolderName,
    String maskedAccountNumber,
    String ifscCode,
    String bankName,
    boolean verified,
    OffsetDateTime createdAt
) {
}
