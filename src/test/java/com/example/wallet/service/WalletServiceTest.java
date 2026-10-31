package com.example.wallet.service;

import com.example.wallet.api.dto.CreateWalletRequest;
import com.example.wallet.api.dto.MovementResponse;
import com.example.wallet.api.dto.PageResponse;
import com.example.wallet.api.dto.UpdateWalletRequest;
import com.example.wallet.api.dto.WalletResponse;
import com.example.wallet.domain.MovementType;
import com.example.wallet.domain.Wallet;
import com.example.wallet.domain.WalletMovement;
import com.example.wallet.domain.WalletStatus;
import com.example.wallet.exception.WalletNotFoundException;
import com.example.wallet.repository.WalletMovementRepository;
import com.example.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletMovementRepository movementRepository;

    @InjectMocks
    private WalletService walletService;

    @Test
    void createTrimsHolderNameAndStartsAtZero() {
        WalletResponse response = walletService.create(new CreateWalletRequest("  Alice  "));

        assertThat(response.holderName()).isEqualTo("Alice");
        assertThat(response.balance()).isEqualByComparingTo("0.00");
        assertThat(response.status()).isEqualTo(WalletStatus.ACTIVE);
        verify(walletRepository).save(any(Wallet.class));
    }

    @Test
    void getThrowsWhenWalletDoesNotExist() {
        UUID walletId = UUID.randomUUID();
        when(walletRepository.findById(walletId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.get(walletId))
                .isInstanceOf(WalletNotFoundException.class);
    }

    @Test
    void updateLocksAndRenamesWallet() {
        Wallet wallet = Wallet.create("Alice");
        when(walletRepository.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));

        WalletResponse response = walletService.update(
                wallet.getId(),
                new UpdateWalletRequest("  Bob  ")
        );

        assertThat(response.holderName()).isEqualTo("Bob");
        verify(walletRepository).findByIdForUpdate(wallet.getId());
    }

    @Test
    void deactivateLocksAndChangesStatus() {
        Wallet wallet = Wallet.create("Alice");
        when(walletRepository.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));

        WalletResponse response = walletService.deactivate(wallet.getId());

        assertThat(response.status()).isEqualTo(WalletStatus.INACTIVE);
        verify(walletRepository).findByIdForUpdate(wallet.getId());
    }

    @Test
    void movementsThrowsWhenWalletDoesNotExist() {
        UUID walletId = UUID.randomUUID();
        when(walletRepository.existsById(walletId)).thenReturn(false);

        assertThatThrownBy(() -> walletService.movements(walletId, 0, 20))
                .isInstanceOf(WalletNotFoundException.class);

        verifyNoInteractions(movementRepository);
    }

    @Test
    void movementsMapsPage() {
        UUID walletId = UUID.randomUUID();
        WalletMovement movement = WalletMovement.create(
                walletId,
                MovementType.DEPOSIT,
                new BigDecimal("10.00"),
                new BigDecimal("10.00"),
                null,
                null,
                "key-1",
                Instant.parse("2026-01-01T00:00:00Z")
        );
        Page<WalletMovement> page = new PageImpl<>(
                List.of(movement),
                PageRequest.of(0, 20),
                1
        );

        when(walletRepository.existsById(walletId)).thenReturn(true);
        when(movementRepository.findByWalletId(eq(walletId), any(Pageable.class))).thenReturn(page);

        PageResponse<MovementResponse> response = walletService.movements(walletId, 0, 20);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().type()).isEqualTo(MovementType.DEPOSIT);
        assertThat(response.content().getFirst().idempotencyKey()).isEqualTo("key-1");
    }

    @Test
    void listFiltersByStatus() {
        Wallet wallet = Wallet.create("Alice");
        Page<Wallet> page = new PageImpl<>(List.of(wallet), PageRequest.of(0, 20), 1);
        when(walletRepository.findAllByStatus(eq(WalletStatus.ACTIVE), any(Pageable.class))).thenReturn(page);

        PageResponse<WalletResponse> response = walletService.list(WalletStatus.ACTIVE, 0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().holderName()).isEqualTo("Alice");
    }
}
