package com.example.wallet.domain;

import com.example.wallet.exception.InvalidAmountException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void normalizesToTwoDecimalPlaces() {
        BigDecimal normalized = Money.normalize(new BigDecimal("10"));

        assertThat(normalized).isEqualByComparingTo("10.00");
        assertThat(normalized.scale()).isEqualTo(2);
    }

    @Test
    void rejectsMoreThanTwoDecimalPlaces() {
        assertThatThrownBy(() -> Money.normalize(new BigDecimal("10.001")))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("2 decimal places");
    }

    @Test
    void rejectsZeroAndNegativeAmounts() {
        assertThatThrownBy(() -> Money.requirePositive(BigDecimal.ZERO))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> Money.requirePositive(new BigDecimal("-1.00")))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void rejectsAmountsAboveNumeric19Scale2() {
        assertThatThrownBy(() -> Money.requirePositive(new BigDecimal("100000000000000000.00")))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("maximum");
    }
}
