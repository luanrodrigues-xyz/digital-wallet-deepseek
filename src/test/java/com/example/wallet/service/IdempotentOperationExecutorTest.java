package com.example.wallet.service;

import com.example.wallet.api.dto.MovementResponse;
import com.example.wallet.api.dto.WalletOperationResponse;
import com.example.wallet.domain.IdempotencyRecord;
import com.example.wallet.domain.IdempotencyStatus;
import com.example.wallet.domain.MovementType;
import com.example.wallet.domain.OperationType;
import com.example.wallet.exception.IdempotencyConflictException;
import com.example.wallet.exception.IdempotencyInProgressException;
import com.example.wallet.exception.InsufficientFundsException;
import com.example.wallet.repository.IdempotencyRecordRepository;
import com.example.wallet.service.command.DepositCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotentOperationExecutorTest {

    @Mock
    private IdempotencyRecordRepository idempotencyRepository;

    private ObjectMapper objectMapper;
    private IdempotentOperationExecutor executor;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        executor = new IdempotentOperationExecutor(idempotencyRepository, objectMapper);
    }

    @Test
    void firstExecutionRunsOperationAndStoresResponseSnapshot() throws Exception {
        UUID walletId = UUID.randomUUID();
        DepositCommand command = DepositCommand.of(walletId, new BigDecimal("10.00"));
        WalletOperationResponse expected = response(walletId, "10.00");

        when(idempotencyRepository.tryInsert(any(UUID.class), eq("key"), eq("DEPOSIT"), anyString()))
                .thenReturn(1);
        when(idempotencyRepository.complete(eq("key"), eq(200), anyString())).thenReturn(1);

        AtomicBoolean called = new AtomicBoolean();
        IdempotentResult<WalletOperationResponse> result = executor.execute(
                "key",
                command,
                WalletOperationResponse.class,
                () -> {
                    called.set(true);
                    return expected;
                }
        );

        assertThat(result.replayed()).isFalse();
        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body()).isEqualTo(expected);
        assertThat(called).isTrue();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(idempotencyRepository).complete(eq("key"), eq(200), body.capture());
        assertThat(body.getValue()).contains("\"balance\":10.00");
    }

    @Test
    void replayReturnsStoredResponseWithoutRunningOperation() throws Exception {
        UUID walletId = UUID.randomUUID();
        DepositCommand command = DepositCommand.of(walletId, new BigDecimal("10.00"));
        WalletOperationResponse expected = response(walletId, "10.00");
        String requestHash = RequestHasher.sha256("DEPOSIT\n" + command.canonicalPayload());
        IdempotencyRecord record = record(
                "key",
                OperationType.DEPOSIT,
                requestHash,
                IdempotencyStatus.COMPLETED,
                objectMapper.writeValueAsString(expected)
        );

        when(idempotencyRepository.tryInsert(any(UUID.class), eq("key"), eq("DEPOSIT"), anyString()))
                .thenReturn(0);
        when(idempotencyRepository.findByIdempotencyKey("key")).thenReturn(Optional.of(record));

        AtomicBoolean called = new AtomicBoolean();
        IdempotentResult<WalletOperationResponse> result = executor.execute(
                "key",
                command,
                WalletOperationResponse.class,
                () -> {
                    called.set(true);
                    return null;
                }
        );

        assertThat(result.replayed()).isTrue();
        assertThat(result.body()).isEqualTo(expected);
        assertThat(called).isFalse();
        verify(idempotencyRepository, never()).complete(anyString(), anyInt(), anyString());
    }

    @Test
    void sameKeyWithDifferentPayloadIsAConflict() {
        UUID walletId = UUID.randomUUID();
        DepositCommand command = DepositCommand.of(walletId, new BigDecimal("10.00"));
        IdempotencyRecord record = record(
                "key",
                OperationType.DEPOSIT,
                "a-different-hash",
                IdempotencyStatus.COMPLETED,
                "{}"
        );

        when(idempotencyRepository.tryInsert(any(UUID.class), eq("key"), eq("DEPOSIT"), anyString()))
                .thenReturn(0);
        when(idempotencyRepository.findByIdempotencyKey("key")).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> executor.execute(
                "key",
                command,
                WalletOperationResponse.class,
                () -> {
                    throw new AssertionError("operation must not run");
                }
        )).isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void inProgressRecordIsNotTreatedAsCompleted() {
        UUID walletId = UUID.randomUUID();
        DepositCommand command = DepositCommand.of(walletId, new BigDecimal("10.00"));
        String requestHash = RequestHasher.sha256("DEPOSIT\n" + command.canonicalPayload());
        IdempotencyRecord record = record(
                "key",
                OperationType.DEPOSIT,
                requestHash,
                IdempotencyStatus.IN_PROGRESS,
                null
        );

        when(idempotencyRepository.tryInsert(any(UUID.class), eq("key"), eq("DEPOSIT"), anyString()))
                .thenReturn(0);
        when(idempotencyRepository.findByIdempotencyKey("key")).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> executor.execute(
                "key",
                command,
                WalletOperationResponse.class,
                () -> {
                    throw new AssertionError("operation must not run");
                }
        )).isInstanceOf(IdempotencyInProgressException.class);
    }

    @Test
    void operationFailureDoesNotMarkRecordCompleted() {
        UUID walletId = UUID.randomUUID();
        DepositCommand command = DepositCommand.of(walletId, new BigDecimal("10.00"));

        when(idempotencyRepository.tryInsert(any(UUID.class), eq("key"), eq("DEPOSIT"), anyString()))
                .thenReturn(1);

        assertThatThrownBy(() -> executor.execute(
                "key",
                command,
                WalletOperationResponse.class,
                () -> {
                    throw new InsufficientFundsException(walletId, new BigDecimal("0.00"), new BigDecimal("10.00"));
                }
        )).isInstanceOf(InsufficientFundsException.class);

        verify(idempotencyRepository, never()).complete(anyString(), anyInt(), anyString());
    }

    private WalletOperationResponse response(UUID walletId, String amount) {
        return new WalletOperationResponse(
                walletId,
                new BigDecimal(amount),
                new MovementResponse(
                        1L,
                        walletId,
                        MovementType.DEPOSIT,
                        new BigDecimal(amount),
                        new BigDecimal(amount),
                        null,
                        null,
                        "key",
                        Instant.parse("2026-01-01T00:00:00Z")
                )
        );
    }

    private IdempotencyRecord record(String key,
                                     OperationType operationType,
                                     String requestHash,
                                     IdempotencyStatus status,
                                     String responseBody) {
        IdempotencyRecord record = newRecord();
        ReflectionTestUtils.setField(record, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(record, "idempotencyKey", key);
        ReflectionTestUtils.setField(record, "operationType", operationType);
        ReflectionTestUtils.setField(record, "requestHash", requestHash);
        ReflectionTestUtils.setField(record, "status", status);
        ReflectionTestUtils.setField(record, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
        if (status == IdempotencyStatus.COMPLETED) {
            ReflectionTestUtils.setField(record, "responseStatus", 200);
            ReflectionTestUtils.setField(record, "responseBody", responseBody);
            ReflectionTestUtils.setField(record, "completedAt", Instant.parse("2026-01-01T00:00:01Z"));
        }
        return record;
    }

    private IdempotencyRecord newRecord() {
        try {
            Constructor<IdempotencyRecord> constructor = IdempotencyRecord.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Could not instantiate IdempotencyRecord", ex);
        }
    }
}
