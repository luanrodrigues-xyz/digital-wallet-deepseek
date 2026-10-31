package com.example.wallet.service.command;

import com.example.wallet.domain.Money;
import com.example.wallet.domain.OperationType;
import com.example.wallet.exception.SameWalletTransferException;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record TransferCommand(UUID sourceWalletId, UUID targetWalletId, BigDecimal amount) implements IdempotentCommand {

    public TransferCommand {
        Objects.requireNonNull(sourceWalletId, "sourceWalletId");
        Objects.requireNonNull(targetWalletId, "targetWalletId");
        if (sourceWalletId.equals(targetWalletId)) {
            throw new SameWalletTransferException(sourceWalletId);
        }
        amount = Money.requirePositive(amount);
    }

    public static TransferCommand of(UUID sourceWalletId, UUID targetWalletId, BigDecimal amount) {
        return new TransferCommand(sourceWalletId, targetWalletId, amount);
    }

    @Override
    public OperationType operationType() {
        return OperationType.TRANSFER;
    }

    @Override
    public String canonicalPayload() {
        return "sourceWalletId=" + sourceWalletId
                + "&targetWalletId=" + targetWalletId
                + "&amount=" + amount.toPlainString();
    }
}
