package com.example.wallet.api;

import com.example.wallet.api.dto.CreateWalletRequest;
import com.example.wallet.api.dto.MovementResponse;
import com.example.wallet.api.dto.PageResponse;
import com.example.wallet.api.dto.UpdateWalletRequest;
import com.example.wallet.api.dto.WalletResponse;
import com.example.wallet.domain.WalletStatus;
import com.example.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v1/wallets", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Tag(name = "Wallets", description = "Wallet lifecycle and movement history")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a wallet", description = "Creates an active wallet with a zero balance.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Wallet created"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<WalletResponse> create(@Valid @RequestBody CreateWalletRequest request) {
        WalletResponse response = walletService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/wallets/" + response.id())).body(response);
    }

    @GetMapping("/{walletId}")
    @Operation(summary = "Get a wallet by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Wallet found"),
            @ApiResponse(responseCode = "404", description = "Wallet not found")
    })
    public WalletResponse get(@PathVariable UUID walletId) {
        return walletService.get(walletId);
    }

    @GetMapping
    @Operation(summary = "List wallets", description = "Returns a page of wallets ordered by newest first.")
    public PageResponse<WalletResponse> list(
            @RequestParam(required = false) WalletStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return walletService.list(status, page, size);
    }

    @PutMapping(path = "/{walletId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update the wallet holder name")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Wallet updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Wallet not found")
    })
    public WalletResponse update(@PathVariable UUID walletId,
                                 @Valid @RequestBody UpdateWalletRequest request) {
        return walletService.update(walletId, request);
    }

    @DeleteMapping("/{walletId}")
    @Operation(summary = "Deactivate a wallet",
            description = "Deactivation is idempotent. The wallet and its history remain readable, "
                    + "but no financial operation is accepted afterwards.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Wallet deactivated"),
            @ApiResponse(responseCode = "404", description = "Wallet not found")
    })
    public WalletResponse deactivate(@PathVariable UUID walletId) {
        return walletService.deactivate(walletId);
    }

    @GetMapping("/{walletId}/movements")
    @Operation(summary = "List wallet movements",
            description = "Returns a page of movements ordered by createdAt DESC, id DESC.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Movements returned"),
            @ApiResponse(responseCode = "404", description = "Wallet not found")
    })
    public PageResponse<MovementResponse> movements(
            @PathVariable UUID walletId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return walletService.movements(walletId, page, size);
    }
}
