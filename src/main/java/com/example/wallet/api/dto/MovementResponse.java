package com.example.wallet.api.dto;

import com.example.wallet.domain.MovementType;
import com.example.wallet.domain.WalletMovement;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MovementResponse(
        Long id,
        UUID walletId,
        MovementType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        UUID relatedWalletId,
        UUID transferId,
        String idempotencyKey,
        Instant createdAt
) {
    public static MovementResponse from(WalletMovement movement) {
        return new MovementResponse(
                movement.getId(),
                movement.getWalletId(),
                movement.getType(),
                movement.getAmount(),
                movement.getBalanceAfter(),
                movement.getRelatedWalletId(),
                movement.getTransferId(),
                movement.getIdempotencyKey(),
                movement.getCreatedAt()
        );
    }
}
