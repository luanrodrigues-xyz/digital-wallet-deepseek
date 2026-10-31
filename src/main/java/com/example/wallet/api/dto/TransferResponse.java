package com.example.wallet.api.dto;

import com.example.wallet.domain.Wallet;
import com.example.wallet.domain.WalletMovement;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TransferResponse(
        UUID transferId,
        UUID sourceWalletId,
        BigDecimal sourceBalance,
        UUID targetWalletId,
        BigDecimal targetBalance,
        List<MovementResponse> movements
) {
    public static TransferResponse of(UUID transferId,
                                      Wallet source,
                                      Wallet target,
                                      WalletMovement sourceMovement,
                                      WalletMovement targetMovement) {
        return new TransferResponse(
                transferId,
                source.getId(),
                source.getBalance(),
                target.getId(),
                target.getBalance(),
                List.of(MovementResponse.from(sourceMovement), MovementResponse.from(targetMovement))
        );
    }
}
