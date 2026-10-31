package com.example.wallet.exception;

public class IdempotencyInProgressException extends RuntimeException {

    private final String idempotencyKey;

    public IdempotencyInProgressException(String idempotencyKey) {
        super("Idempotency key is still being processed: " + idempotencyKey);
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
