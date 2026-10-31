package com.example.wallet.service.command;

import com.example.wallet.domain.Money;
import com.example.wallet.domain.OperationType;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record WithdrawCommand(UUID walletId, BigDecimal amount) implements IdempotentCommand {

    public WithdrawCommand {
        Objects.requireNonNull(walletId, "walletId");
        amount = Money.requirePositive(amount);
    }

    public static WithdrawCommand of(UUID walletId, BigDecimal amount) {
        return new WithdrawCommand(walletId, amount);
    }

    @Override
    public OperationType operationType() {
        return OperationType.WITHDRAWAL;
    }

    @Override
    public String canonicalPayload() {
        return "walletId=" + walletId + "&amount=" + amount.toPlainString();
    }
}
