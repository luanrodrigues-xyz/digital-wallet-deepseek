package com.example.wallet.exception;

import java.util.UUID;

public class SameWalletTransferException extends RuntimeException {

    private final UUID walletId;

    public SameWalletTransferException(UUID walletId) {
        super("Source and target wallets must be different");
        this.walletId = walletId;
    }

    public UUID getWalletId() {
        return walletId;
    }
}
