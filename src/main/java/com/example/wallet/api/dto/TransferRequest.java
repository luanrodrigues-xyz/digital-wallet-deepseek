package com.example.wallet.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
        @NotNull(message = "sourceWalletId is required")
        UUID sourceWalletId,

        @NotNull(message = "targetWalletId is required")
        UUID targetWalletId,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
        @Digits(integer = 17, fraction = 2, message = "amount must have at most 17 integer digits and 2 decimals")
        BigDecimal amount
) {
}
