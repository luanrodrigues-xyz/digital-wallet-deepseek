package com.example.wallet.exception;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {

    private final UUID walletId;
    private final BigDecimal availableBalance;
    private final BigDecimal requestedAmount;

    public InsufficientFundsException(UUID walletId, BigDecimal availableBalance, BigDecimal requestedAmount) {
        super("Insufficient funds in wallet " + walletId);
        this.walletId = walletId;
        this.availableBalance = availableBalance;
        this.requestedAmount = requestedAmount;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }
}
