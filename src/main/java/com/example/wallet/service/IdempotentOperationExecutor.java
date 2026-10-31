package com.example.wallet.service;

import com.example.wallet.domain.IdempotencyRecord;
import com.example.wallet.domain.IdempotencyStatus;
import com.example.wallet.exception.IdempotencyConflictException;
import com.example.wallet.exception.IdempotencyInProgressException;
import com.example.wallet.repository.IdempotencyRecordRepository;
import com.example.wallet.service.command.IdempotentCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Owns the transaction that claims the idempotency key, executes the financial
 * operation and stores the response snapshot. Claim + money movement + response
 * snapshot commit or roll back together.
 */
@Service
public class IdempotentOperationExecutor {

    private static final Logger log = LoggerFactory.getLogger(IdempotentOperationExecutor.class);
    private static final int SUCCESS_STATUS = 200;

    private final IdempotencyRecordRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public IdempotentOperationExecutor(IdempotencyRecordRepository idempotencyRepository,
                                       ObjectMapper objectMapper) {
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public <T> IdempotentResult<T> execute(String idempotencyKey,
                                           IdempotentCommand command,
                                           Class<T> responseType,
                                           Supplier<T> operation) {
        String requestHash = RequestHasher.sha256(
                command.operationType().name() + "\n" + command.canonicalPayload()
        );

        int inserted = idempotencyRepository.tryInsert(
                UUID.randomUUID(),
                idempotencyKey,
                command.operationType().name(),
                requestHash
        );

        if (inserted == 0) {
            IdempotencyRecord existing = idempotencyRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException(
                            "Idempotency record not found after conflict: " + idempotencyKey));
            return replay(existing, requestHash, responseType);
        }

        if (inserted != 1) {
            throw new IllegalStateException("Unexpected idempotency insert result: " + inserted);
        }

        T body = operation.get();

        String responseBody;
        try {
            responseBody = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize idempotent response", ex);
        }

        int completed = idempotencyRepository.complete(idempotencyKey, SUCCESS_STATUS, responseBody);
        if (completed != 1) {
            throw new IllegalStateException("Could not complete idempotency record: " + idempotencyKey);
        }

        return new IdempotentResult<>(body, SUCCESS_STATUS, false);
    }

    private <T> IdempotentResult<T> replay(IdempotencyRecord record,
                                           String requestHash,
                                           Class<T> responseType) {
        if (!record.getRequestHash().equals(requestHash)) {
            log.warn("Idempotency key {} reused with a different request payload", record.getIdempotencyKey());
            throw new IdempotencyConflictException(record.getIdempotencyKey());
        }
        if (record.getStatus() != IdempotencyStatus.COMPLETED) {
            throw new IdempotencyInProgressException(record.getIdempotencyKey());
        }
        if (record.getResponseStatus() == null || record.getResponseBody() == null) {
            throw new IllegalStateException(
                    "Completed idempotency record has no stored response: " + record.getIdempotencyKey());
        }

        try {
            T body = objectMapper.readValue(record.getResponseBody(), responseType);
            log.debug("Replaying idempotent {} response for key {}",
                    record.getOperationType(), record.getIdempotencyKey());
            return new IdempotentResult<>(body, record.getResponseStatus(), true);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not deserialize idempotent response", ex);
        }
    }
}
