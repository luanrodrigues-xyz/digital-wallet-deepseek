package com.example.wallet.integration;

import com.example.wallet.api.dto.WalletOperationResponse;
import com.example.wallet.api.dto.WalletResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyIntegrationTest extends AbstractIntegrationTest {

    @Test
    void transferReplayAfterDeactivationReturnsOriginalResult() {
        WalletResponse source = createWallet("Source");
        WalletResponse target = createWallet("Target");
        depositOk(source.id(), "100.00", "seed");
        ResponseEntity<JsonNode> original = transferRaw(source.id(), target.id(), "25.00", "transfer");
        assertThat(original.getStatusCode()).isEqualTo(HttpStatus.OK);
        depositOk(target.id(), "10.00", "later");
        rest.exchange("/api/v1/wallets/" + source.id(), HttpMethod.DELETE, null, WalletResponse.class);

        ResponseEntity<JsonNode> replay = transferRaw(source.id(), target.id(), "25.00", "transfer");

        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody()).isEqualTo(original.getBody());
        assertThat(replay.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(balanceInDb(source.id())).isEqualByComparingTo("75.00");
        assertThat(balanceInDb(target.id())).isEqualByComparingTo("35.00");
        assertThat(countMovements(source.id())).isEqualTo(2);
        assertThat(countMovements(target.id())).isEqualTo(2);
    }

    @Test
    void sameKeyForAnotherWalletConflicts() {
        WalletResponse first = createWallet("First");
        WalletResponse second = createWallet("Second");
        depositOk(first.id(), "10.00", "global-key");

        assertProblem(depositRaw(second.id(), "10.00", "global-key"),
                HttpStatus.CONFLICT, "idempotency-key-conflict");
        assertThat(balanceInDb(second.id())).isEqualByComparingTo("0.00");
        assertThat(countMovements(second.id())).isZero();
    }

    @Test
    void repeatedRequestReturnsOriginalSnapshotWithoutMovingMoneyAgain() {
        WalletResponse wallet = createWallet("Alice");

        ResponseEntity<JsonNode> first = depositRaw(wallet.id(), "10.00", "key-1");
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getHeaders().getFirst("Idempotent-Replayed")).isNull();
        WalletOperationResponse firstBody = read(first.getBody(), WalletOperationResponse.class);

        // A later operation changes the current balance. The retry must still
        // return the original response snapshot, not the current wallet state.
        depositOk(wallet.id(), "5.00", "key-2");

        ResponseEntity<JsonNode> replay = depositRaw(wallet.id(), "10.00", "key-1");
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        WalletOperationResponse replayBody = read(replay.getBody(), WalletOperationResponse.class);

        assertThat(replayBody).isEqualTo(firstBody);
        assertThat(replayBody.balance()).isEqualByComparingTo("10.00");
        assertThat(getWallet(wallet.id()).balance()).isEqualByComparingTo("15.00");
        assertThat(countMovements(wallet.id())).isEqualTo(2);
        assertThat(idempotencyRecordCount()).isEqualTo(2);
    }

    @Test
    void sameKeyWithDifferentPayloadReturns409AndDoesNotMoveMoney() {
        WalletResponse wallet = createWallet("Alice");
        depositOk(wallet.id(), "10.00", "conflict-key");

        ResponseEntity<JsonNode> conflict = depositRaw(wallet.id(), "20.00", "conflict-key");
        JsonNode problem = assertProblem(conflict, HttpStatus.CONFLICT, "idempotency-key-conflict");
        assertThat(problem.path("idempotencyKey").asText()).isEqualTo("conflict-key");

        assertThat(getWallet(wallet.id()).balance()).isEqualByComparingTo("10.00");
        assertThat(countMovements(wallet.id())).isEqualTo(1);
        assertThat(idempotencyRecordCount()).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentOperationReturns409() {
        WalletResponse wallet = createWallet("Alice");
        depositOk(wallet.id(), "10.00", "operation-key");

        ResponseEntity<JsonNode> conflict = withdrawRaw(wallet.id(), "10.00", "operation-key");
        assertProblem(conflict, HttpStatus.CONFLICT, "idempotency-key-conflict");

        assertThat(getWallet(wallet.id()).balance()).isEqualByComparingTo("10.00");
        assertThat(countMovements(wallet.id())).isEqualTo(1);
    }

    @Test
    void amountScaleIsCanonicalForIdempotency() {
        WalletResponse wallet = createWallet("Alice");

        ResponseEntity<JsonNode> first = depositRaw(wallet.id(), "10", "scale-key");
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> replay = depositRaw(wallet.id(), "10.00", "scale-key");
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");

        assertThat(getWallet(wallet.id()).balance()).isEqualByComparingTo("10.00");
        assertThat(countMovements(wallet.id())).isEqualTo(1);
        assertThat(idempotencyRecordCount()).isEqualTo(1);
    }
}
