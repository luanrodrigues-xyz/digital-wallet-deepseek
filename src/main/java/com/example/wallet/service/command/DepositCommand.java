package com.example.wallet.service.command;

import com.example.wallet.domain.Money;
import com.example.wallet.domain.OperationType;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record DepositCommand(UUID walletId, BigDecimal amount) implements IdempotentCommand {

    public DepositCommand {
        Objects.requireNonNull(walletId, "walletId");
        amount = Money.requirePositive(amount);
    }

    public static DepositCommand of(UUID walletId, BigDecimal amount) {
        return new DepositCommand(walletId, amount);
    }

    @Override
    public OperationType operationType() {
        return OperationType.DEPOSIT;
    }

    @Override
    public String canonicalPayload() {
        return "walletId=" + walletId + "&amount=" + amount.toPlainString();
    }
}
