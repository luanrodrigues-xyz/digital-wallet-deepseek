package com.example.wallet.service.command;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CommandFingerprintTest {

    @Test
    void depositFingerprintIgnoresDecimalScale() {
        UUID walletId = UUID.randomUUID();

        DepositCommand first = DepositCommand.of(walletId, new BigDecimal("10"));
        DepositCommand second = DepositCommand.of(walletId, new BigDecimal("10.00"));

        assertThat(first.canonicalPayload()).isEqualTo(second.canonicalPayload());
    }

    @Test
    void transferFingerprintIncludesDirection() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        TransferCommand aToB = TransferCommand.of(first, second, new BigDecimal("10.00"));
        TransferCommand bToA = TransferCommand.of(second, first, new BigDecimal("10.00"));

        assertThat(aToB.canonicalPayload()).isNotEqualTo(bToA.canonicalPayload());
    }
}
