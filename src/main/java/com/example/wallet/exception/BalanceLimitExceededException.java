package com.example.wallet.exception;

import java.math.BigDecimal;
import java.util.UUID;

public class BalanceLimitExceededException extends RuntimeException {

    private final UUID walletId;
    private final BigDecimal currentBalance;
    private final BigDecimal amount;

    public BalanceLimitExceededException(UUID walletId, BigDecimal currentBalance, BigDecimal amount) {
        super("Credit would exceed the maximum balance for wallet " + walletId);
        this.walletId = walletId;
        this.currentBalance = currentBalance;
        this.amount = amount;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
