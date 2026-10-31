package com.example.wallet.service;

public record IdempotentResult<T>(T body, int status, boolean replayed) {
}
