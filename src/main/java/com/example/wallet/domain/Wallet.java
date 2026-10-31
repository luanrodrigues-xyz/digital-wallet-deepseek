package com.example.wallet.domain;

import com.example.wallet.exception.BalanceLimitExceededException;
import com.example.wallet.exception.InsufficientFundsException;
import com.example.wallet.exception.WalletInactiveException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "holder_name", nullable = false, length = 120)
    private String holderName;

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WalletStatus status;

    /**
     * Optimistic version is a second line of defence. Financial operations use
     * pessimistic row locks; this catches accidental lost updates on paths that
     * do not take the lock.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Wallet() {
    }

    public static Wallet create(String holderName) {
        if (holderName == null || holderName.isBlank()) {
            throw new IllegalArgumentException("holderName is required");
        }
        Wallet wallet = new Wallet();
        wallet.id = UUID.randomUUID();
        wallet.holderName = holderName;
        wallet.balance = Money.zero();
        wallet.status = WalletStatus.ACTIVE;
        Instant now = now();
        wallet.createdAt = now;
        wallet.updatedAt = now;
        return wallet;
    }

    public void rename(String newHolderName) {
        if (newHolderName == null || newHolderName.isBlank()) {
            throw new IllegalArgumentException("holderName is required");
        }
        this.holderName = newHolderName;
    }

    public void deactivate() {
        this.status = WalletStatus.INACTIVE;
    }

    public void ensureActive() {
        if (status != WalletStatus.ACTIVE) {
            throw new WalletInactiveException(id, status);
        }
    }

    public void debit(BigDecimal amount) {
        BigDecimal normalized = Money.requirePositive(amount);
        ensureActive();
        if (balance.compareTo(normalized) < 0) {
            throw new InsufficientFundsException(id, balance, normalized);
        }
        this.balance = balance.subtract(normalized).setScale(Money.SCALE);
    }

    public void credit(BigDecimal amount) {
        BigDecimal normalized = Money.requirePositive(amount);
        ensureActive();
        BigDecimal newBalance = balance.add(normalized).setScale(Money.SCALE);
        if (newBalance.compareTo(Money.MAX_BALANCE) > 0) {
            throw new BalanceLimitExceededException(id, balance, normalized);
        }
        this.balance = newBalance;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = now();
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public String getHolderName() {
        return holderName;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public WalletStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
