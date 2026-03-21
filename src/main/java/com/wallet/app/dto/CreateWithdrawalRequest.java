package com.wallet.app.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record CreateWithdrawalRequest(
    @NotNull
    UUID bankAccountId,

    @NotNull
    @DecimalMin(value = "0.0001", message = "amount must be greater than zero")
    BigDecimal amount
) {
}
