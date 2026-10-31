package com.example.wallet.integration;

import com.example.wallet.api.dto.WalletOperationResponse;
import com.example.wallet.api.dto.WalletResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Timeout(60)
class ConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private ExecutorService executor;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void createExecutor() {
        executor = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void twoConcurrentWithdrawalsFromSameWalletOnlyOneSucceeds() throws Exception {
        WalletResponse wallet = createWallet("Alice");
        depositOk(wallet.id(), "100.00", "seed-key");

        List<ResponseEntity<JsonNode>> results = runTogether(
                () -> withdrawRaw(wallet.id(), "60.00", "withdraw-1"),
                () -> withdrawRaw(wallet.id(), "60.00", "withdraw-2")
        );

        long successes = results.stream().filter(r -> r.getStatusCode() == HttpStatus.OK).count();
        long failures = results.stream()
                .filter(r -> r.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY)
                .count();
        assertThat(successes).isEqualTo(1);
        assertThat(failures).isEqualTo(1);

        ResponseEntity<JsonNode> failed = results.stream()
                .filter(r -> r.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY)
                .findFirst()
                .orElseThrow();
        assertProblem(failed, HttpStatus.UNPROCESSABLE_ENTITY, "insufficient-funds");

        assertThat(getWallet(wallet.id()).balance()).isEqualByComparingTo("40.00");
        assertThat(countMovements(wallet.id())).isEqualTo(2);
        assertThat(idempotencyRecordCount()).isEqualTo(2);
    }

    @Test
    void oppositeTransfersDoNotDeadlock() throws Exception {
        WalletResponse a = createWallet("A");
        WalletResponse b = createWallet("B");
        depositOk(a.id(), "100.00", "a-seed");
        depositOk(b.id(), "100.00", "b-seed");

        List<ResponseEntity<JsonNode>> results = runTogether(
                () -> transferRaw(a.id(), b.id(), "60.00", "a-to-b"),
                () -> transferRaw(b.id(), a.id(), "60.00", "b-to-a")
        );

        assertThat(results).allSatisfy(response ->
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK));

        assertThat(getWallet(a.id()).balance()).isEqualByComparingTo("100.00");
        assertThat(getWallet(b.id()).balance()).isEqualByComparingTo("100.00");
        assertThat(countMovements(a.id())).isEqualTo(3);
        assertThat(countMovements(b.id())).isEqualTo(3);
    }

    @Test
    void concurrentTransfersFromSameSourceOnlyOneSucceeds() throws Exception {
        WalletResponse source = createWallet("Source");
        WalletResponse targetOne = createWallet("Target one");
        WalletResponse targetTwo = createWallet("Target two");
        depositOk(source.id(), "100.00", "source-seed");

        List<ResponseEntity<JsonNode>> results = runTogether(
                () -> transferRaw(source.id(), targetOne.id(), "60.00", "transfer-one"),
                () -> transferRaw(source.id(), targetTwo.id(), "60.00", "transfer-two")
        );

        long successes = results.stream().filter(r -> r.getStatusCode() == HttpStatus.OK).count();
        long failures = results.stream()
                .filter(r -> r.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY)
                .count();
        assertThat(successes).isEqualTo(1);
        assertThat(failures).isEqualTo(1);

        assertThat(getWallet(source.id()).balance()).isEqualByComparingTo("40.00");
        BigDecimal targetTotal = balanceInDb(targetOne.id()).add(balanceInDb(targetTwo.id()));
        assertThat(targetTotal).isEqualByComparingTo("60.00");
        assertThat(countMovements(source.id())).isEqualTo(2);
    }

    @Test
    void concurrentRequestsWithSameIdempotencyKeyExecuteOnce() throws Exception {
        WalletResponse wallet = createWallet("Alice");

        List<ResponseEntity<JsonNode>> results = runTogether(
                () -> depositRaw(wallet.id(), "50.00", "same-key"),
                () -> depositRaw(wallet.id(), "50.00", "same-key")
        );

        assertThat(results).allSatisfy(response ->
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK));

        WalletOperationResponse first = read(results.get(0).getBody(), WalletOperationResponse.class);
        WalletOperationResponse second = read(results.get(1).getBody(), WalletOperationResponse.class);
        assertThat(first).isEqualTo(second);

        long replays = results.stream()
                .filter(r -> "true".equals(r.getHeaders().getFirst("Idempotent-Replayed")))
                .count();
        assertThat(replays).isEqualTo(1);
        assertThat(getWallet(wallet.id()).balance()).isEqualByComparingTo("50.00");
        assertThat(countMovements(wallet.id())).isEqualTo(1);
        assertThat(idempotencyRecordCount()).isEqualTo(1);
    }

    @Test
    void concurrentSameKeyWithDifferentPayloadHasOneWinnerAndOneConflict() throws Exception {
        WalletResponse wallet = createWallet("Alice");

        List<ResponseEntity<JsonNode>> results = runTogether(
                () -> depositRaw(wallet.id(), "10.00", "conflict-key"),
                () -> depositRaw(wallet.id(), "20.00", "conflict-key")
        );

        long successes = results.stream().filter(r -> r.getStatusCode() == HttpStatus.OK).count();
        long conflicts = results.stream().filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).count();
        assertThat(successes).isEqualTo(1);
        assertThat(conflicts).isEqualTo(1);

        ResponseEntity<JsonNode> conflict = results.stream()
                .filter(r -> r.getStatusCode() == HttpStatus.CONFLICT)
                .findFirst()
                .orElseThrow();
        assertProblem(conflict, HttpStatus.CONFLICT, "idempotency-key-conflict");

        assertThat(getWallet(wallet.id()).balance())
                .isIn(new BigDecimal("10.00"), new BigDecimal("20.00"));
        assertThat(countMovements(wallet.id())).isEqualTo(1);
        assertThat(idempotencyRecordCount()).isEqualTo(1);
    }

    @Test
    void withdrawalWaitsForConcurrentDeactivationAndUsesCommittedStatus() throws Exception {
        WalletResponse wallet = createWallet("Alice");
        depositOk(wallet.id(), "100.00", "seed");
        Future<ResponseEntity<JsonNode>> withdrawal;
        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try {
                try (var statement = blocker.prepareStatement("update wallets set status = 'INACTIVE' where id = ?")) {
                    statement.setObject(1, wallet.id());
                    statement.executeUpdate();
                }
                withdrawal = executor.submit(() -> withdrawRaw(wallet.id(), "60.00", "withdraw"));
                awaitBlocked(1);
                blocker.commit();
            } finally {
                blocker.rollback();
            }
        }
        assertProblem(withdrawal.get(10, TimeUnit.SECONDS),
                HttpStatus.UNPROCESSABLE_ENTITY, "wallet-inactive");
        assertThat(balanceInDb(wallet.id())).isEqualByComparingTo("100.00");
        assertThat(countMovements(wallet.id())).isEqualTo(1);
        assertThat(idempotencyRecordCount()).isEqualTo(1);
    }

    @Test
    void waitingDuplicateClaimsKeyWhenOriginalTransactionRollsBack() throws Exception {
        WalletResponse wallet = createWallet("Alice");
        Future<ResponseEntity<JsonNode>> retry;
        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try {
                try (var statement = blocker.prepareStatement("""
                        insert into idempotency_records
                        (id, idempotency_key, operation_type, request_hash, status, created_at)
                        values (?, 'rolled-back-key', 'DEPOSIT', ?, 'IN_PROGRESS', now())
                        """)) {
                    statement.setObject(1, UUID.randomUUID());
                    statement.setString(2, "0".repeat(64));
                    statement.executeUpdate();
                }
                retry = executor.submit(() -> depositRaw(wallet.id(), "25.00", "rolled-back-key"));
                awaitBlocked(1);
            } finally {
                blocker.rollback();
            }
        }
        ResponseEntity<JsonNode> result = retry.get(10, TimeUnit.SECONDS);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getHeaders().getFirst("Idempotent-Replayed")).isNull();
        assertThat(balanceInDb(wallet.id())).isEqualByComparingTo("25.00");
        assertThat(countMovements(wallet.id())).isEqualTo(1);
        assertThat(idempotencyRecordCount()).isEqualTo(1);
    }

    private void awaitBlocked(int count) {
        await().pollDelay(Duration.ZERO).pollInterval(Duration.ofMillis(10))
                .atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
                    Integer blocked = jdbc.queryForObject("""
                            select count(*) from pg_stat_activity
                            where datname = current_database()
                              and cardinality(pg_blocking_pids(pid)) > 0
                            """, Integer.class);
                    assertThat(blocked).isEqualTo(count);
                });
    }

    @SafeVarargs
    private final List<ResponseEntity<JsonNode>> runTogether(
            Callable<ResponseEntity<JsonNode>>... tasks) throws Exception {
        CountDownLatch ready = new CountDownLatch(tasks.length);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<JsonNode>>> futures = new ArrayList<>();

        for (Callable<ResponseEntity<JsonNode>> task : tasks) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for concurrent start");
                }
                return task.call();
            }));
        }

        // Hold real row locks until every HTTP transaction is blocked in PostgreSQL.
        // A client-side start barrier alone does not prove database contention.
        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try {
                try (var statement = blocker.createStatement();
                     var rows = statement.executeQuery("select id from wallets for update")) {
                    while (rows.next()) {
                        rows.getObject(1);
                    }
                }
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                awaitBlocked(tasks.length);
                blocker.commit();
            } finally {
                blocker.rollback();
            }
        }

        List<ResponseEntity<JsonNode>> results = new ArrayList<>();
        for (Future<ResponseEntity<JsonNode>> future : futures) {
            results.add(future.get(20, TimeUnit.SECONDS));
        }
        return results;
    }
}
