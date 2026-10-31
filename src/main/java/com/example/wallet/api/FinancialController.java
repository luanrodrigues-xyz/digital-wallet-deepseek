package com.example.wallet.api;

import com.example.wallet.api.dto.AmountRequest;
import com.example.wallet.api.dto.TransferRequest;
import com.example.wallet.api.dto.TransferResponse;
import com.example.wallet.api.dto.WalletOperationResponse;
import com.example.wallet.service.FinancialOperationService;
import com.example.wallet.service.IdempotentOperationExecutor;
import com.example.wallet.service.IdempotentResult;
import com.example.wallet.service.command.DepositCommand;
import com.example.wallet.service.command.TransferCommand;
import com.example.wallet.service.command.WithdrawCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Tag(name = "Financial operations",
        description = "Deposits, withdrawals and transfers. Every operation requires an Idempotency-Key header.")
public class FinancialController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final IdempotentOperationExecutor idempotentExecutor;
    private final FinancialOperationService financialOperationService;

    public FinancialController(IdempotentOperationExecutor idempotentExecutor,
                               FinancialOperationService financialOperationService) {
        this.idempotentExecutor = idempotentExecutor;
        this.financialOperationService = financialOperationService;
    }

    @PostMapping(path = "/wallets/{walletId}/deposits", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Deposit money into a wallet",
            description = "The same Idempotency-Key returns the original result without moving money again.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deposit executed or replayed"),
            @ApiResponse(responseCode = "400", description = "Invalid request or idempotency key"),
            @ApiResponse(responseCode = "404", description = "Wallet not found"),
            @ApiResponse(responseCode = "409", description = "Idempotency key conflict",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Wallet inactive or balance limit exceeded",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<WalletOperationResponse> deposit(
            @PathVariable UUID walletId,
            @Parameter(name = IDEMPOTENCY_KEY_HEADER, in = ParameterIn.HEADER, required = true,
                    description = "Client-generated unique key, 1-128 printable ASCII characters",
                    schema = @Schema(type = "string", maxLength = 128))
            @RequestHeader(IDEMPOTENCY_KEY_HEADER)
            @NotBlank @Size(max = 128) @Pattern(regexp = "^[\\x21-\\x7E]+$") String idempotencyKey,
            @Valid @RequestBody AmountRequest request) {

        DepositCommand command = DepositCommand.of(walletId, request.amount());
        IdempotentResult<WalletOperationResponse> result = idempotentExecutor.execute(
                idempotencyKey,
                command,
                WalletOperationResponse.class,
                () -> financialOperationService.deposit(command, idempotencyKey)
        );
        return response(result);
    }

    @PostMapping(path = "/wallets/{walletId}/withdrawals", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Withdraw money from a wallet",
            description = "The balance can never become negative. The same Idempotency-Key returns the original result.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Withdrawal executed or replayed"),
            @ApiResponse(responseCode = "400", description = "Invalid request or idempotency key"),
            @ApiResponse(responseCode = "404", description = "Wallet not found"),
            @ApiResponse(responseCode = "409", description = "Idempotency key conflict",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Insufficient funds or wallet inactive",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<WalletOperationResponse> withdraw(
            @PathVariable UUID walletId,
            @Parameter(name = IDEMPOTENCY_KEY_HEADER, in = ParameterIn.HEADER, required = true,
                    description = "Client-generated unique key, 1-128 printable ASCII characters",
                    schema = @Schema(type = "string", maxLength = 128))
            @RequestHeader(IDEMPOTENCY_KEY_HEADER)
            @NotBlank @Size(max = 128) @Pattern(regexp = "^[\\x21-\\x7E]+$") String idempotencyKey,
            @Valid @RequestBody AmountRequest request) {

        WithdrawCommand command = WithdrawCommand.of(walletId, request.amount());
        IdempotentResult<WalletOperationResponse> result = idempotentExecutor.execute(
                idempotencyKey,
                command,
                WalletOperationResponse.class,
                () -> financialOperationService.withdraw(command, idempotencyKey)
        );
        return response(result);
    }

    @PostMapping(path = "/transfers", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Transfer money between wallets",
            description = "Both wallets are locked in deterministic UUID order. The transfer is atomic: "
                    + "either both balances and both movements are committed, or nothing is.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transfer executed or replayed"),
            @ApiResponse(responseCode = "400", description = "Invalid request or idempotency key"),
            @ApiResponse(responseCode = "404", description = "Wallet not found"),
            @ApiResponse(responseCode = "409", description = "Idempotency key conflict",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Insufficient funds, inactive wallet, same wallet or balance limit",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<TransferResponse> transfer(
            @Parameter(name = IDEMPOTENCY_KEY_HEADER, in = ParameterIn.HEADER, required = true,
                    description = "Client-generated unique key, 1-128 printable ASCII characters",
                    schema = @Schema(type = "string", maxLength = 128))
            @RequestHeader(IDEMPOTENCY_KEY_HEADER)
            @NotBlank @Size(max = 128) @Pattern(regexp = "^[\\x21-\\x7E]+$") String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        TransferCommand command = TransferCommand.of(
                request.sourceWalletId(),
                request.targetWalletId(),
                request.amount()
        );
        IdempotentResult<TransferResponse> result = idempotentExecutor.execute(
                idempotencyKey,
                command,
                TransferResponse.class,
                () -> financialOperationService.transfer(command, idempotencyKey)
        );
        return response(result);
    }

    private <T> ResponseEntity<T> response(IdempotentResult<T> result) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(result.status());
        if (result.replayed()) {
            builder.header("Idempotent-Replayed", "true");
        }
        return builder.body(result.body());
    }
}
