package com.example.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "wallet_movements")
public class WalletMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30, updatable = false)
    private MovementType type;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal balanceAfter;

    @Column(name = "related_wallet_id", updatable = false)
    private UUID relatedWalletId;

    @Column(name = "transfer_id", updatable = false)
    private UUID transferId;

    @Column(name = "idempotency_key", length = 128, updatable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WalletMovement() {
    }

    public static WalletMovement create(UUID walletId,
                                        MovementType type,
                                        BigDecimal amount,
                                        BigDecimal balanceAfter,
                                        UUID relatedWalletId,
                                        UUID transferId,
                                        String idempotencyKey,
                                        Instant createdAt) {
        Objects.requireNonNull(walletId, "walletId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(createdAt, "createdAt");

        boolean transfer = type == MovementType.TRANSFER_IN || type == MovementType.TRANSFER_OUT;
        if (transfer && (transferId == null || relatedWalletId == null)) {
            throw new IllegalArgumentException("Transfer movements require transferId and relatedWalletId");
        }
        if (!transfer && (transferId != null || relatedWalletId != null)) {
            throw new IllegalArgumentException("Deposit/withdrawal movements cannot have transfer metadata");
        }

        WalletMovement movement = new WalletMovement();
        movement.walletId = walletId;
        movement.type = type;
        movement.amount = Money.requirePositive(amount);
        movement.balanceAfter = Money.normalize(balanceAfter);
        movement.relatedWalletId = relatedWalletId;
        movement.transferId = transferId;
        movement.idempotencyKey = idempotencyKey;
        movement.createdAt = createdAt;
        return movement;
    }

    public Long getId() {
        return id;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public MovementType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public UUID getRelatedWalletId() {
        return relatedWalletId;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
