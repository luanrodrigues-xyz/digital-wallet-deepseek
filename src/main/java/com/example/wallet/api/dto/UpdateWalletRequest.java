package com.example.wallet.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateWalletRequest(
        @NotBlank(message = "holderName is required")
        @Size(max = 120, message = "holderName must have at most 120 characters")
        String holderName
) {
}
