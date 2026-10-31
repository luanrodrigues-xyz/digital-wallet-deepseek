package com.example.wallet.service;

import com.example.wallet.api.dto.TransferResponse;
import com.example.wallet.api.dto.WalletOperationResponse;
import com.example.wallet.domain.MovementType;
import com.example.wallet.domain.Wallet;
import com.example.wallet.domain.WalletMovement;
import com.example.wallet.exception.InsufficientFundsException;
import com.example.wallet.exception.SameWalletTransferException;
import com.example.wallet.exception.WalletInactiveException;
import com.example.wallet.exception.WalletNotFoundException;
import com.example.wallet.repository.WalletMovementRepository;
import com.example.wallet.repository.WalletRepository;
import com.example.wallet.service.command.DepositCommand;
import com.example.wallet.service.command.TransferCommand;
import com.example.wallet.service.command.WithdrawCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialOperationServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletMovementRepository movementRepository;

    @InjectMocks
    private FinancialOperationService financialOperationService;

    @Test
    void depositCreditsWalletAndCreatesMovement() {
        Wallet wallet = Wallet.create("Alice");
        when(walletRepository.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(movementRepository.save(any(WalletMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WalletOperationResponse response = financialOperationService.deposit(
                DepositCommand.of(wallet.getId(), new BigDecimal("10.00")),
                "deposit-key"
        );

        assertThat(wallet.getBalance()).isEqualByComparingTo("10.00");
        assertThat(response.balance()).isEqualByComparingTo("10.00");
        assertThat(response.movement().type()).isEqualTo(MovementType.DEPOSIT);
        assertThat(response.movement().idempotencyKey()).isEqualTo("deposit-key");
        verify(movementRepository).save(any(WalletMovement.class));
    }

    @Test
    void insufficientFundsDoesNotPersistMovement() {
        Wallet wallet = Wallet.create("Alice");
        wallet.credit(new BigDecimal("10.00"));
        when(walletRepository.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> financialOperationService.withdraw(
                WithdrawCommand.of(wallet.getId(), new BigDecimal("20.00")),
                "withdraw-key"
        )).isInstanceOf(InsufficientFundsException.class);

        assertThat(wallet.getBalance()).isEqualByComparingTo("10.00");
        verify(movementRepository, never()).save(any(WalletMovement.class));
    }

    @Test
    void transferLocksWalletsInUuidOrderAndMovesMoney() {
        Wallet source = Wallet.create("Source");
        Wallet target = Wallet.create("Target");
        source.credit(new BigDecimal("100.00"));
        target.credit(new BigDecimal("50.00"));

        when(walletRepository.findByIdForUpdate(source.getId())).thenReturn(Optional.of(source));
        when(walletRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(movementRepository.save(any(WalletMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransferResponse response = financialOperationService.transfer(
                TransferCommand.of(source.getId(), target.getId(), new BigDecimal("30.00")),
                "transfer-key"
        );

        UUID firstId = source.getId().compareTo(target.getId()) < 0 ? source.getId() : target.getId();
        UUID secondId = firstId.equals(source.getId()) ? target.getId() : source.getId();
        InOrder inOrder = inOrder(walletRepository);
        inOrder.verify(walletRepository).findByIdForUpdate(firstId);
        inOrder.verify(walletRepository).findByIdForUpdate(secondId);

        assertThat(source.getBalance()).isEqualByComparingTo("70.00");
        assertThat(target.getBalance()).isEqualByComparingTo("80.00");
        assertThat(response.sourceBalance()).isEqualByComparingTo("70.00");
        assertThat(response.targetBalance()).isEqualByComparingTo("80.00");
        assertThat(response.transferId()).isNotNull();

        ArgumentCaptor<WalletMovement> movements = ArgumentCaptor.forClass(WalletMovement.class);
        verify(movementRepository, times(2)).save(movements.capture());
        assertThat(movements.getAllValues())
                .extracting(WalletMovement::getType)
                .containsExactlyInAnyOrder(MovementType.TRANSFER_OUT, MovementType.TRANSFER_IN);
    }

    @Test
    void transferToInactiveTargetDoesNotDebitSource() {
        Wallet source = Wallet.create("Source");
        Wallet target = Wallet.create("Target");
        source.credit(new BigDecimal("100.00"));
        target.deactivate();

        when(walletRepository.findByIdForUpdate(source.getId())).thenReturn(Optional.of(source));
        when(walletRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> financialOperationService.transfer(
                TransferCommand.of(source.getId(), target.getId(), new BigDecimal("30.00")),
                "transfer-key"
        )).isInstanceOf(WalletInactiveException.class);

        assertThat(source.getBalance()).isEqualByComparingTo("100.00");
        verify(movementRepository, never()).save(any(WalletMovement.class));
    }

    @Test
    void transferFromInactiveSourceDoesNotMoveMoney() {
        Wallet source = Wallet.create("Source");
        Wallet target = Wallet.create("Target");
        source.credit(new BigDecimal("100.00"));
        source.deactivate();

        when(walletRepository.findByIdForUpdate(source.getId())).thenReturn(Optional.of(source));
        when(walletRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> financialOperationService.transfer(
                TransferCommand.of(source.getId(), target.getId(), new BigDecimal("30.00")),
                "transfer-key"
        )).isInstanceOf(WalletInactiveException.class);

        assertThat(source.getBalance()).isEqualByComparingTo("100.00");
        assertThat(target.getBalance()).isEqualByComparingTo("0.00");
        verify(movementRepository, never()).save(any(WalletMovement.class));
    }

    @Test
    void sameWalletTransferIsRejectedBeforeAnyLock() {
        UUID walletId = UUID.randomUUID();

        assertThatThrownBy(() -> TransferCommand.of(walletId, walletId, new BigDecimal("10.00")))
                .isInstanceOf(SameWalletTransferException.class);

        verify(walletRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void missingWalletIsReported() {
        UUID walletId = UUID.randomUUID();
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> financialOperationService.deposit(
                DepositCommand.of(walletId, new BigDecimal("10.00")),
                "deposit-key"
        )).isInstanceOf(WalletNotFoundException.class);

        verify(movementRepository, never()).save(any(WalletMovement.class));
    }
}
