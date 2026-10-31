package com.example.wallet.service.command;

import com.example.wallet.domain.OperationType;

/**
 * A financial command whose canonical representation is stable across retries.
 * Implementations must normalize money before producing the fingerprint.
 */
public interface IdempotentCommand {

    OperationType operationType();

    String canonicalPayload();
}
