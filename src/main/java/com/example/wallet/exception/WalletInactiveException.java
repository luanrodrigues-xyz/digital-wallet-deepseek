package com.example.wallet.exception;

import com.example.wallet.domain.WalletStatus;

import java.util.UUID;

public class WalletInactiveException extends RuntimeException {

    private final UUID walletId;
    private final WalletStatus status;

    public WalletInactiveException(UUID walletId, WalletStatus status) {
        super("Wallet " + walletId + " is not active (status=" + status + ")");
        this.walletId = walletId;
        this.status = status;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public WalletStatus getStatus() {
        return status;
    }
}
