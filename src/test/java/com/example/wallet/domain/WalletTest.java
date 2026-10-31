package com.example.wallet.domain;

import com.example.wallet.exception.BalanceLimitExceededException;
import com.example.wallet.exception.InsufficientFundsException;
import com.example.wallet.exception.WalletInactiveException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletTest {

    @Test
    void newWalletStartsActiveWithZeroBalance() {
        Wallet wallet = Wallet.create("Alice");

        assertThat(wallet.getId()).isNotNull();
        assertThat(wallet.getHolderName()).isEqualTo("Alice");
        assertThat(wallet.getBalance()).isEqualByComparingTo("0.00");
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        assertThat(wallet.getCreatedAt()).isNotNull();
        assertThat(wallet.getUpdatedAt()).isNotNull();
    }

    @Test
    void creditAndDebitKeepTwoDecimalScale() {
        Wallet wallet = Wallet.create("Alice");

        wallet.credit(new BigDecimal("10.00"));
        wallet.debit(new BigDecimal("4.50"));

        assertThat(wallet.getBalance()).isEqualByComparingTo("5.50");
        assertThat(wallet.getBalance().scale()).isEqualTo(2);
    }

    @Test
    void debitCannotMakeBalanceNegative() {
        Wallet wallet = Wallet.create("Alice");
        wallet.credit(new BigDecimal("10.00"));

        assertThatThrownBy(() -> wallet.debit(new BigDecimal("10.01")))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(wallet.getBalance()).isEqualByComparingTo("10.00");
    }

    @Test
    void debitOfExactBalanceReachesZero() {
        Wallet wallet = Wallet.create("Alice");
        wallet.credit(new BigDecimal("10.00"));

        wallet.debit(new BigDecimal("10.00"));

        assertThat(wallet.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void inactiveWalletRejectsCreditAndDebit() {
        Wallet wallet = Wallet.create("Alice");
        wallet.credit(new BigDecimal("10.00"));
        wallet.deactivate();

        assertThatThrownBy(() -> wallet.credit(new BigDecimal("1.00")))
                .isInstanceOf(WalletInactiveException.class);
        assertThatThrownBy(() -> wallet.debit(new BigDecimal("1.00")))
                .isInstanceOf(WalletInactiveException.class);
        assertThat(wallet.getBalance()).isEqualByComparingTo("10.00");
    }

    @Test
    void deactivateIsIdempotent() {
        Wallet wallet = Wallet.create("Alice");

        wallet.deactivate();
        wallet.deactivate();

        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.INACTIVE);
    }

    @Test
    void creditRejectsBalanceAboveNumericLimit() {
        Wallet wallet = Wallet.create("Alice");
        wallet.credit(Money.MAX_BALANCE);

        assertThatThrownBy(() -> wallet.credit(new BigDecimal("0.01")))
                .isInstanceOf(BalanceLimitExceededException.class);

        assertThat(wallet.getBalance()).isEqualByComparingTo(Money.MAX_BALANCE);
    }
}
