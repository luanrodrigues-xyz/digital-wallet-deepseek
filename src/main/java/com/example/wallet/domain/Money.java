package com.example.wallet.domain;

import com.example.wallet.exception.InvalidAmountException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money rules in one place. All persisted amounts use numeric(19,2).
 */
public final class Money {

    public static final int SCALE = 2;

    /**
     * Largest value that fits numeric(19,2): 17 integer digits and 2 decimals.
     */
    public static final BigDecimal MAX_BALANCE = new BigDecimal("99999999999999999.99");

    private Money() {
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            throw new InvalidAmountException("Amount is required");
        }
        try {
            return value.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new InvalidAmountException("Amount must have at most 2 decimal places");
        }
    }

    public static BigDecimal requirePositive(BigDecimal value) {
        BigDecimal normalized = normalize(value);
        if (normalized.signum() <= 0) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }
        if (normalized.compareTo(MAX_BALANCE) > 0) {
            throw new InvalidAmountException("Amount exceeds the maximum allowed value");
        }
        return normalized;
    }
}
