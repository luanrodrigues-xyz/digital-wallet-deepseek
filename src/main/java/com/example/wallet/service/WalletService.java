package com.example.wallet.service;

import com.example.wallet.api.dto.CreateWalletRequest;
import com.example.wallet.api.dto.MovementResponse;
import com.example.wallet.api.dto.PageResponse;
import com.example.wallet.api.dto.UpdateWalletRequest;
import com.example.wallet.api.dto.WalletResponse;
import com.example.wallet.domain.Wallet;
import com.example.wallet.domain.WalletMovement;
import com.example.wallet.domain.WalletStatus;
import com.example.wallet.exception.WalletNotFoundException;
import com.example.wallet.repository.WalletMovementRepository;
import com.example.wallet.repository.WalletRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletMovementRepository movementRepository;

    public WalletService(WalletRepository walletRepository, WalletMovementRepository movementRepository) {
        this.walletRepository = walletRepository;
        this.movementRepository = movementRepository;
    }

    @Transactional
    public WalletResponse create(CreateWalletRequest request) {
        Wallet wallet = Wallet.create(request.holderName().trim());
        walletRepository.save(wallet);
        return WalletResponse.from(wallet);
    }

    @Transactional(readOnly = true)
    public WalletResponse get(UUID walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
        return WalletResponse.from(wallet);
    }

    @Transactional(readOnly = true)
    public PageResponse<WalletResponse> list(WalletStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        ));
        Page<Wallet> result = status == null
                ? walletRepository.findAll(pageable)
                : walletRepository.findAllByStatus(status, pageable);
        return PageResponse.from(result, WalletResponse::from);
    }

    @Transactional
    public WalletResponse update(UUID walletId, UpdateWalletRequest request) {
        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
        wallet.rename(request.holderName().trim());
        walletRepository.flush();
        return WalletResponse.from(wallet);
    }

    @Transactional
    public WalletResponse deactivate(UUID walletId) {
        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
        wallet.deactivate();
        walletRepository.flush();
        return WalletResponse.from(wallet);
    }

    @Transactional(readOnly = true)
    public PageResponse<MovementResponse> movements(UUID walletId, int page, int size) {
        if (!walletRepository.existsById(walletId)) {
            throw new WalletNotFoundException(walletId);
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        ));
        Page<WalletMovement> result = movementRepository.findByWalletId(walletId, pageable);
        return PageResponse.from(result, MovementResponse::from);
    }
}
