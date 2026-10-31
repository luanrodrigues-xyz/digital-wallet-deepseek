package com.example.wallet.api.dto;

import com.example.wallet.domain.Wallet;
import com.example.wallet.domain.WalletMovement;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WalletOperationResponse(
        UUID walletId,
        BigDecimal balance,
        MovementResponse movement
) {
    public static WalletOperationResponse of(Wallet wallet, WalletMovement movement) {
        return new WalletOperationResponse(
                wallet.getId(),
                wallet.getBalance(),
                MovementResponse.from(movement)
        );
    }
}
