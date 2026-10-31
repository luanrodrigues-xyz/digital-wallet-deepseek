package com.example.wallet.service;

import com.example.wallet.api.dto.TransferResponse;
import com.example.wallet.api.dto.WalletOperationResponse;
import com.example.wallet.domain.MovementType;
import com.example.wallet.domain.Wallet;
import com.example.wallet.domain.WalletMovement;
import com.example.wallet.exception.SameWalletTransferException;
import com.example.wallet.exception.WalletNotFoundException;
import com.example.wallet.repository.WalletMovementRepository;
import com.example.wallet.repository.WalletRepository;
import com.example.wallet.service.command.DepositCommand;
import com.example.wallet.service.command.TransferCommand;
import com.example.wallet.service.command.WithdrawCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class FinancialOperationService {

    private final WalletRepository walletRepository;
    private final WalletMovementRepository movementRepository;

    public FinancialOperationService(WalletRepository walletRepository,
                                     WalletMovementRepository movementRepository) {
        this.walletRepository = walletRepository;
        this.movementRepository = movementRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public WalletOperationResponse deposit(DepositCommand command, String idempotencyKey) {
        Wallet wallet = lockWallet(command.walletId());
        wallet.ensureActive();
        wallet.credit(command.amount());

        WalletMovement movement = WalletMovement.create(
                wallet.getId(),
                MovementType.DEPOSIT,
                command.amount(),
                wallet.getBalance(),
                null,
                null,
                idempotencyKey,
                now()
        );
        movementRepository.save(movement);
        return WalletOperationResponse.of(wallet, movement);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public WalletOperationResponse withdraw(WithdrawCommand command, String idempotencyKey) {
        Wallet wallet = lockWallet(command.walletId());
        wallet.ensureActive();
        wallet.debit(command.amount());

        WalletMovement movement = WalletMovement.create(
                wallet.getId(),
                MovementType.WITHDRAWAL,
                command.amount(),
                wallet.getBalance(),
                null,
                null,
                idempotencyKey,
                now()
        );
        movementRepository.save(movement);
        return WalletOperationResponse.of(wallet, movement);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public TransferResponse transfer(TransferCommand command, String idempotencyKey) {
        if (command.sourceWalletId().equals(command.targetWalletId())) {
            throw new SameWalletTransferException(command.sourceWalletId());
        }

        /*
         * Lock acquisition order is the global UUID order, never the request
         * order. A transaction therefore never holds a larger UUID while
         * waiting for a smaller one, which makes a lock cycle impossible.
         */
        UUID firstId = command.sourceWalletId().compareTo(command.targetWalletId()) < 0
                ? command.sourceWalletId()
                : command.targetWalletId();
        UUID secondId = firstId.equals(command.sourceWalletId())
                ? command.targetWalletId()
                : command.sourceWalletId();

        Wallet first = lockWallet(firstId);
        Wallet second = lockWallet(secondId);

        Wallet source = first.getId().equals(command.sourceWalletId()) ? first : second;
        Wallet target = first.getId().equals(command.targetWalletId()) ? first : second;

        // Fail before mutating either entity if one side is not operable.
        source.ensureActive();
        target.ensureActive();

        source.debit(command.amount());
        target.credit(command.amount());

        UUID transferId = UUID.randomUUID();
        Instant now = now();

        WalletMovement sourceMovement = WalletMovement.create(
                source.getId(),
                MovementType.TRANSFER_OUT,
                command.amount(),
                source.getBalance(),
                target.getId(),
                transferId,
                idempotencyKey,
                now
        );
        WalletMovement targetMovement = WalletMovement.create(
                target.getId(),
                MovementType.TRANSFER_IN,
                command.amount(),
                target.getBalance(),
                source.getId(),
                transferId,
                idempotencyKey,
                now
        );

        movementRepository.save(sourceMovement);
        movementRepository.save(targetMovement);

        return TransferResponse.of(transferId, source, target, sourceMovement, targetMovement);
    }

    private Wallet lockWallet(UUID walletId) {
        return walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
