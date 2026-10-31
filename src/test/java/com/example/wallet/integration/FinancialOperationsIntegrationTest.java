package com.example.wallet.integration;

import com.example.wallet.api.dto.MovementResponse;
import com.example.wallet.api.dto.PageResponse;
import com.example.wallet.api.dto.TransferResponse;
import com.example.wallet.api.dto.WalletOperationResponse;
import com.example.wallet.api.dto.WalletResponse;
import com.example.wallet.domain.MovementType;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialOperationsIntegrationTest extends AbstractIntegrationTest {

    @Test
    void failureOnSecondMovementRollsBackFirstMovementBalancesAndKey() {
        WalletResponse source = createWallet("Source");
        WalletResponse target = createWallet("Target");
        depositOk(source.id(), "100.00", "seed");
        // A real database error after TRANSFER_OUT has already been inserted.
        jdbc.execute("""
                alter table wallet_movements add constraint test_reject_transfer_in
                check (type <> 'TRANSFER_IN')
                """);
        try {
            ResponseEntity<JsonNode> failure = transferRaw(source.id(), target.id(), "30.00", "retry-key");
            assertProblem(failure, HttpStatus.INTERNAL_SERVER_ERROR, "data-integrity-violation");
            assertThat(balanceInDb(source.id())).isEqualByComparingTo("100.00");
            assertThat(balanceInDb(target.id())).isEqualByComparingTo("0.00");
            assertThat(countMovements(source.id())).isEqualTo(1);
            assertThat(countMovements(target.id())).isZero();
            assertThat(idempotencyRecordCount()).isEqualTo(1);
        } finally {
            jdbc.execute("alter table wallet_movements drop constraint test_reject_transfer_in");
        }
        transferOk(source.id(), target.id(), "30.00", "retry-key");
        assertThat(balanceInDb(source.id())).isEqualByComparingTo("70.00");
        assertThat(balanceInDb(target.id())).isEqualByComparingTo("30.00");
    }

    @Test
    void withdrawalFromInactiveWalletIsRejected() {
        WalletResponse wallet = createWallet("Alice");
        depositOk(wallet.id(), "10.00", "seed");
        rest.exchange("/api/v1/wallets/" + wallet.id(), HttpMethod.DELETE, null, WalletResponse.class);

        assertProblem(withdrawRaw(wallet.id(), "5.00", "inactive-withdrawal"),
                HttpStatus.UNPROCESSABLE_ENTITY, "wallet-inactive");
        assertThat(balanceInDb(wallet.id())).isEqualByComparingTo("10.00");
        assertThat(countMovements(wallet.id())).isEqualTo(1);
        assertThat(idempotencyRecordCount()).isEqualTo(1);
    }

    @Test
    void depositAndWithdrawUpdateBalanceAndHistory() {
        WalletResponse wallet = createWallet("Alice");

        WalletOperationResponse deposit = depositOk(wallet.id(), "100.00", "deposit-key");
        assertThat(deposit.balance()).isEqualByComparingTo("100.00");
        assertThat(deposit.movement().id()).isNotNull();
        assertThat(deposit.movement().type()).isEqualTo(MovementType.DEPOSIT);
        assertThat(deposit.movement().idempotencyKey()).isEqualTo("deposit-key");

        WalletOperationResponse withdrawal = withdrawOk(wallet.id(), "30.00", "withdraw-key");
        assertThat(withdrawal.balance()).isEqualByComparingTo("70.00");
        assertThat(withdrawal.movement().type()).isEqualTo(MovementType.WITHDRAWAL);

        assertThat(getWallet(wallet.id()).balance()).isEqualByComparingTo("70.00");
        assertThat(balanceInDb(wallet.id())).isEqualByComparingTo("70.00");
        assertThat(countMovements(wallet.id())).isEqualTo(2);
    }

    @Test
    void insufficientFundsReturns422AndLeavesNoPartialState() {
        WalletResponse wallet = createWallet("Alice");
        depositOk(wallet.id(), "10.00", "seed-key");

        ResponseEntity<JsonNode> response = withdrawRaw(wallet.id(), "20.00", "failed-withdraw-key");
        JsonNode problem = assertProblem(response, HttpStatus.UNPROCESSABLE_ENTITY, "insufficient-funds");
        assertThat(problem.path("walletId").asText()).isEqualTo(wallet.id().toString());
        assertThat(problem.path("availableBalance").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(problem.path("requestedAmount").decimalValue()).isEqualByComparingTo("20.00");

        assertThat(getWallet(wallet.id()).balance()).isEqualByComparingTo("10.00");
        assertThat(countMovements(wallet.id())).isEqualTo(1);
        assertThat(idempotencyRecordCount()).isEqualTo(1);

        /*
         * Failed operations roll back the idempotency claim as well. No money
         * moved, so reusing the key is safe and intentional.
         */
        WalletOperationResponse retry = withdrawOk(wallet.id(), "5.00", "failed-withdraw-key");
        assertThat(retry.balance()).isEqualByComparingTo("5.00");
        assertThat(countMovements(wallet.id())).isEqualTo(2);
    }

    @Test
    void transferIsAtomicAndCreatesTwoLinkedMovements() {
        WalletResponse source = createWallet("Source");
        WalletResponse target = createWallet("Target");
        depositOk(source.id(), "100.00", "source-seed");
        depositOk(target.id(), "20.00", "target-seed");

        TransferResponse transfer = transferOk(source.id(), target.id(), "30.00", "transfer-key");

        assertThat(transfer.transferId()).isNotNull();
        assertThat(transfer.sourceBalance()).isEqualByComparingTo("70.00");
        assertThat(transfer.targetBalance()).isEqualByComparingTo("50.00");
        assertThat(transfer.movements()).hasSize(2);
        assertThat(transfer.movements())
                .extracting(MovementResponse::type)
                .containsExactlyInAnyOrder(MovementType.TRANSFER_OUT, MovementType.TRANSFER_IN);
        assertThat(transfer.movements())
                .extracting(MovementResponse::transferId)
                .containsOnly(transfer.transferId());

        assertThat(getWallet(source.id()).balance()).isEqualByComparingTo("70.00");
        assertThat(getWallet(target.id()).balance()).isEqualByComparingTo("50.00");
        assertThat(countMovements(source.id())).isEqualTo(2);
        assertThat(countMovements(target.id())).isEqualTo(2);
    }

    @Test
    void transferToInactiveTargetDoesNotDebitSource() {
        WalletResponse source = createWallet("Source");
        WalletResponse target = createWallet("Target");
        depositOk(source.id(), "100.00", "source-seed");
        rest.exchange(
                "/api/v1/wallets/" + target.id(),
                HttpMethod.DELETE,
                null,
                WalletResponse.class
        );

        ResponseEntity<JsonNode> response = transferRaw(source.id(), target.id(), "30.00", "transfer-key");
        JsonNode problem = assertProblem(response, HttpStatus.UNPROCESSABLE_ENTITY, "wallet-inactive");
        assertThat(problem.path("walletId").asText()).isEqualTo(target.id().toString());

        assertThat(getWallet(source.id()).balance()).isEqualByComparingTo("100.00");
        assertThat(getWallet(target.id()).balance()).isEqualByComparingTo("0.00");
        assertThat(countMovements(source.id())).isEqualTo(1);
        assertThat(countMovements(target.id())).isZero();
        assertThat(idempotencyRecordCount()).isEqualTo(1);
    }

    @Test
    void transferFromInactiveSourceIsRejected() {
        WalletResponse source = createWallet("Source");
        WalletResponse target = createWallet("Target");
        depositOk(source.id(), "100.00", "source-seed");
        rest.exchange(
                "/api/v1/wallets/" + source.id(),
                HttpMethod.DELETE,
                null,
                WalletResponse.class
        );

        ResponseEntity<JsonNode> response = transferRaw(source.id(), target.id(), "30.00", "transfer-key");
        JsonNode problem = assertProblem(response, HttpStatus.UNPROCESSABLE_ENTITY, "wallet-inactive");
        assertThat(problem.path("walletId").asText()).isEqualTo(source.id().toString());

        assertThat(getWallet(source.id()).balance()).isEqualByComparingTo("100.00");
        assertThat(getWallet(target.id()).balance()).isEqualByComparingTo("0.00");
        assertThat(countMovements(source.id())).isEqualTo(1);
        assertThat(countMovements(target.id())).isZero();
    }

    @Test
    void depositToInactiveWalletIsRejected() {
        WalletResponse wallet = createWallet("Alice");
        depositOk(wallet.id(), "10.00", "seed-key");
        rest.exchange(
                "/api/v1/wallets/" + wallet.id(),
                HttpMethod.DELETE,
                null,
                WalletResponse.class
        );

        ResponseEntity<JsonNode> response = depositRaw(wallet.id(), "5.00", "inactive-deposit-key");
        assertProblem(response, HttpStatus.UNPROCESSABLE_ENTITY, "wallet-inactive");

        assertThat(getWallet(wallet.id()).balance()).isEqualByComparingTo("10.00");
        assertThat(countMovements(wallet.id())).isEqualTo(1);
    }

    @Test
    void balanceLimitFailureRollsBackSourceDebitAndMovements() {
        WalletResponse source = createWallet("Source");
        WalletResponse target = createWallet("Target");
        depositOk(source.id(), "100.00", "source-seed");
        depositOk(target.id(), "99999999999999999.99", "target-max");

        ResponseEntity<JsonNode> response = transferRaw(source.id(), target.id(), "1.00", "overflow-key");
        JsonNode problem = assertProblem(response, HttpStatus.UNPROCESSABLE_ENTITY, "balance-limit-exceeded");
        assertThat(problem.path("walletId").asText()).isEqualTo(target.id().toString());

        assertThat(getWallet(source.id()).balance()).isEqualByComparingTo("100.00");
        assertThat(getWallet(target.id()).balance()).isEqualByComparingTo("99999999999999999.99");
        assertThat(balanceInDb(source.id())).isEqualByComparingTo("100.00");
        assertThat(balanceInDb(target.id())).isEqualByComparingTo("99999999999999999.99");
        assertThat(countMovements(source.id())).isEqualTo(1);
        assertThat(countMovements(target.id())).isEqualTo(1);
        assertThat(idempotencyRecordCount()).isEqualTo(2);
    }

    @Test
    void transferToSameWalletIsRejectedWithoutClaimingKey() {
        WalletResponse wallet = createWallet("Alice");
        depositOk(wallet.id(), "10.00", "seed-key");

        ResponseEntity<JsonNode> response = transferRaw(wallet.id(), wallet.id(), "5.00", "same-wallet-key");
        JsonNode problem = assertProblem(response, HttpStatus.UNPROCESSABLE_ENTITY, "same-wallet-transfer");
        assertThat(problem.path("walletId").asText()).isEqualTo(wallet.id().toString());

        assertThat(getWallet(wallet.id()).balance()).isEqualByComparingTo("10.00");
        assertThat(countMovements(wallet.id())).isEqualTo(1);
        assertThat(idempotencyRecordCount()).isEqualTo(1);
    }

    @Test
    void transferToMissingWalletDoesNotDebitSource() {
        WalletResponse source = createWallet("Source");
        depositOk(source.id(), "10.00", "source-seed");
        UUID missing = UUID.randomUUID();

        ResponseEntity<JsonNode> response = transferRaw(source.id(), missing, "5.00", "missing-target-key");
        JsonNode problem = assertProblem(response, HttpStatus.NOT_FOUND, "wallet-not-found");
        assertThat(problem.path("walletId").asText()).isEqualTo(missing.toString());

        assertThat(getWallet(source.id()).balance()).isEqualByComparingTo("10.00");
        assertThat(countMovements(source.id())).isEqualTo(1);
        assertThat(idempotencyRecordCount()).isEqualTo(1);
    }

    @Test
    void historyIsPaginatedNewestFirst() {
        WalletResponse wallet = createWallet("Alice");
        for (int i = 1; i <= 5; i++) {
            depositOk(wallet.id(), i + ".00", "deposit-" + i);
        }

        ResponseEntity<PageResponse<MovementResponse>> firstPage = movements(wallet.id(), 0, 2);
        assertThat(firstPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstPage.getBody()).isNotNull();
        assertThat(firstPage.getBody().totalElements()).isEqualTo(5);
        assertThat(firstPage.getBody().totalPages()).isEqualTo(3);
        assertThat(firstPage.getBody().content()).hasSize(2);
        assertThat(firstPage.getBody().content())
                .extracting(MovementResponse::amount)
                .containsExactly(new BigDecimal("5.00"), new BigDecimal("4.00"));
        assertThat(firstPage.getBody().first()).isTrue();
        assertThat(firstPage.getBody().last()).isFalse();

        ResponseEntity<PageResponse<MovementResponse>> lastPage = movements(wallet.id(), 2, 2);
        assertThat(lastPage.getBody()).isNotNull();
        assertThat(lastPage.getBody().content()).hasSize(1);
        assertThat(lastPage.getBody().content().getFirst().amount()).isEqualByComparingTo("1.00");
        assertThat(lastPage.getBody().first()).isFalse();
        assertThat(lastPage.getBody().last()).isTrue();
    }
}
