package com.example.wallet.api;

import com.example.wallet.exception.BalanceLimitExceededException;
import com.example.wallet.exception.IdempotencyConflictException;
import com.example.wallet.exception.IdempotencyInProgressException;
import com.example.wallet.exception.InsufficientFundsException;
import com.example.wallet.exception.InvalidAmountException;
import com.example.wallet.exception.SameWalletTransferException;
import com.example.wallet.exception.WalletInactiveException;
import com.example.wallet.exception.WalletNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * HIGHEST_PRECEDENCE so this advice wins over Spring Boot's built-in
 * ProblemDetailsExceptionHandler, which would otherwise answer framework
 * exceptions (405, 415, 404, malformed JSON, ...) with "about:blank".
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final URI PROBLEM_BASE = URI.create("https://wallet.example/problems/");

    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleWalletNotFound(WalletNotFoundException ex,
                                                               HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.NOT_FOUND, "Wallet not found",
                ex.getMessage(), "wallet-not-found", request);
        problem.setProperty("walletId", ex.getWalletId());
        return response(problem);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ProblemDetail> handleInsufficientFunds(InsufficientFundsException ex,
                                                                  HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds",
                "The wallet balance is not enough for this operation.", "insufficient-funds", request);
        problem.setProperty("walletId", ex.getWalletId());
        problem.setProperty("availableBalance", ex.getAvailableBalance());
        problem.setProperty("requestedAmount", ex.getRequestedAmount());
        return response(problem);
    }

    @ExceptionHandler(WalletInactiveException.class)
    public ResponseEntity<ProblemDetail> handleWalletInactive(WalletInactiveException ex,
                                                               HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Wallet is not active",
                "Financial operations are only allowed on active wallets.", "wallet-inactive", request);
        problem.setProperty("walletId", ex.getWalletId());
        // Never reuse "status": ProblemDetail already owns that member (HTTP status code).
        problem.setProperty("walletStatus", ex.getStatus());
        return response(problem);
    }

    @ExceptionHandler(SameWalletTransferException.class)
    public ResponseEntity<ProblemDetail> handleSameWalletTransfer(SameWalletTransferException ex,
                                                                   HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid transfer",
                ex.getMessage(), "same-wallet-transfer", request);
        problem.setProperty("walletId", ex.getWalletId());
        return response(problem);
    }

    @ExceptionHandler(BalanceLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleBalanceLimit(BalanceLimitExceededException ex,
                                                             HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Balance limit exceeded",
                "The credit would exceed the maximum supported balance.", "balance-limit-exceeded", request);
        problem.setProperty("walletId", ex.getWalletId());
        problem.setProperty("currentBalance", ex.getCurrentBalance());
        problem.setProperty("amount", ex.getAmount());
        return response(problem);
    }

    @ExceptionHandler(InvalidAmountException.class)
    public ResponseEntity<ProblemDetail> handleInvalidAmount(InvalidAmountException ex,
                                                              HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid amount",
                ex.getMessage(), "invalid-amount", request);
        return response(problem);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ProblemDetail> handleIdempotencyConflict(IdempotencyConflictException ex,
                                                                    HttpServletRequest request) {
        log.warn("Idempotency conflict for key {}", ex.getIdempotencyKey());
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Idempotency key conflict",
                "This Idempotency-Key was already used with a different request payload.",
                "idempotency-key-conflict", request);
        problem.setProperty("idempotencyKey", ex.getIdempotencyKey());
        return response(problem);
    }

    @ExceptionHandler(IdempotencyInProgressException.class)
    public ResponseEntity<ProblemDetail> handleIdempotencyInProgress(IdempotencyInProgressException ex,
                                                                      HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Idempotency key in progress",
                "The original request is still being processed. Retry later with the same key.",
                "idempotency-in-progress", request);
        problem.setProperty("idempotencyKey", ex.getIdempotencyKey());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleBodyValidation(MethodArgumentNotValidException ex,
                                                               HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more request fields are invalid.", "validation-failed", request);
        List<Map<String, Object>> errors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.add(validationError(fieldError.getField(), fieldError.getDefaultMessage()));
        }
        for (ObjectError objectError : ex.getBindingResult().getGlobalErrors()) {
            errors.add(validationError(objectError.getObjectName(), objectError.getDefaultMessage()));
        }
        problem.setProperty("errors", errors);
        return response(problem);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleMethodValidation(HandlerMethodValidationException ex,
                                                                 HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more request parameters are invalid.", "validation-failed", request);
        List<Map<String, Object>> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String parameter = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error -> errors.add(
                    validationError(parameter == null ? "request" : parameter, error.getDefaultMessage())
            ));
        });
        problem.setProperty("errors", errors);
        return response(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex,
                                                                    HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more request parameters are invalid.", "validation-failed", request);
        List<Map<String, Object>> errors = new ArrayList<>();
        ex.getConstraintViolations().forEach(violation -> errors.add(
                validationError(violation.getPropertyPath().toString(), violation.getMessage())
        ));
        problem.setProperty("errors", errors);
        return response(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                               HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Malformed request",
                "The request body is missing or is not valid JSON.", "malformed-request", request);
        return response(problem);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                             HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid parameter",
                "Parameter '" + ex.getName() + "' has an invalid value.", "invalid-parameter", request);
        problem.setProperty("parameter", ex.getName());
        return response(problem);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> handleMissingHeader(MissingRequestHeaderException ex,
                                                              HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Missing header",
                "Required header '" + ex.getHeaderName() + "' is missing.", "missing-header", request);
        problem.setProperty("header", ex.getHeaderName());
        return response(problem);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParameter(MissingServletRequestParameterException ex,
                                                                 HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Missing parameter",
                "Required parameter '" + ex.getParameterName() + "' is missing.", "missing-parameter", request);
        problem.setProperty("parameter", ex.getParameterName());
        return response(problem);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                   HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed",
                "HTTP method is not supported for this resource.", "method-not-allowed", request);
        HttpHeaders headers = new HttpHeaders();
        if (ex.getSupportedHttpMethods() != null) {
            headers.setAllow(ex.getSupportedHttpMethods());
        }
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(problem, headers, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex,
                                                                     HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type",
                "Use Content-Type: application/json.", "unsupported-media-type", request);
        return response(problem);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ProblemDetail> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex,
                                                              HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.NOT_ACCEPTABLE, "Not acceptable",
                "The requested response media type is not available.", "not-acceptable", request);
        return response(problem);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ProblemDetail> handleNotFound(Exception ex, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.NOT_FOUND, "Resource not found",
                "No resource exists at this path.", "resource-not-found", request);
        return response(problem);
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleLockFailure(PessimisticLockingFailureException ex,
                                                            HttpServletRequest request) {
        log.warn("Could not acquire database lock: {}", ex.getMessage());
        ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE, "Resource busy",
                "The wallet is locked by another operation. Retry the request.", "resource-locked", request);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
                                                               HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Concurrent modification",
                "The wallet was modified by another request. Retry the operation.",
                "optimistic-lock-conflict", request);
        return response(problem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex,
                                                              HttpServletRequest request) {
        log.error("Data integrity violation", ex);
        ProblemDetail problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, "Data integrity violation",
                "The operation could not be completed because of a data integrity constraint.",
                "data-integrity-violation", request);
        return response(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error", ex);
        ProblemDetail problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred.", "internal-error", request);
        return response(problem);
    }

    private ProblemDetail problem(HttpStatus status,
                                  String title,
                                  String detail,
                                  String type,
                                  HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(PROBLEM_BASE.resolve(type));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    private ResponseEntity<ProblemDetail> response(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private Map<String, Object> validationError(String field, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("field", field);
        error.put("message", message == null ? "invalid" : message);
        return error;
    }
}
