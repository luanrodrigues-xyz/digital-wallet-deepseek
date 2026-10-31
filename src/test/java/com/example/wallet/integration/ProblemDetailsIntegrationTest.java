package com.example.wallet.integration;

import com.example.wallet.api.dto.AmountRequest;
import com.example.wallet.api.dto.CreateWalletRequest;
import com.example.wallet.api.dto.UpdateWalletRequest;
import com.example.wallet.api.dto.WalletResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemDetailsIntegrationTest extends AbstractIntegrationTest {

    @Test
    void bodyValidationReturnsRfc9457ProblemDetails() {
        ResponseEntity<JsonNode> response = rest.postForEntity(
                "/api/v1/wallets",
                new CreateWalletRequest("   "),
                JsonNode.class
        );

        JsonNode problem = assertProblem(response, HttpStatus.BAD_REQUEST, "validation-failed");
        assertThat(problem.path("errors").isArray()).isTrue();
        assertThat(problem.path("errors").get(0).path("field").asText()).isEqualTo("holderName");
        assertThat(problem.has("trace")).isFalse();
        assertThat(problem.has("exception")).isFalse();
        assertThat(problem.has("stackTrace")).isFalse();
    }

    @Test
    void invalidAmountReturnsValidationProblem() {
        WalletResponse wallet = createWallet("Alice");

        ResponseEntity<JsonNode> zero = depositRaw(wallet.id(), "0.00", "zero-key");
        assertProblem(zero, HttpStatus.BAD_REQUEST, "validation-failed");

        ResponseEntity<JsonNode> tooManyDecimals = depositRaw(wallet.id(), "10.001", "decimals-key");
        assertProblem(tooManyDecimals, HttpStatus.BAD_REQUEST, "validation-failed");
    }

    @Test
    void missingIdempotencyKeyReturns400() {
        WalletResponse wallet = createWallet("Alice");

        ResponseEntity<JsonNode> response = rest.exchange(
                "/api/v1/wallets/" + wallet.id() + "/deposits",
                HttpMethod.POST,
                new HttpEntity<>(new AmountRequest(new BigDecimal("10.00")), jsonHeaders()),
                JsonNode.class
        );

        JsonNode problem = assertProblem(response, HttpStatus.BAD_REQUEST, "missing-header");
        assertThat(problem.path("header").asText()).isEqualTo("Idempotency-Key");
    }

    @Test
    void invalidIdempotencyKeyReturns400() {
        WalletResponse wallet = createWallet("Alice");

        ResponseEntity<JsonNode> response = rest.exchange(
                "/api/v1/wallets/" + wallet.id() + "/deposits",
                HttpMethod.POST,
                new HttpEntity<>(new AmountRequest(new BigDecimal("10.00")), idempotencyHeaders("bad key")),
                JsonNode.class
        );

        assertProblem(response, HttpStatus.BAD_REQUEST, "validation-failed");
    }

    @Test
    void invalidUuidPathReturns400() {
        ResponseEntity<JsonNode> response = rest.getForEntity(
                "/api/v1/wallets/not-a-uuid",
                JsonNode.class
        );

        JsonNode problem = assertProblem(response, HttpStatus.BAD_REQUEST, "invalid-parameter");
        assertThat(problem.path("parameter").asText()).isEqualTo("walletId");
    }

    @Test
    void missingWalletReturns404() {
        UUID missing = UUID.randomUUID();

        ResponseEntity<JsonNode> response = rest.getForEntity(
                "/api/v1/wallets/" + missing,
                JsonNode.class
        );

        JsonNode problem = assertProblem(response, HttpStatus.NOT_FOUND, "wallet-not-found");
        assertThat(problem.path("walletId").asText()).isEqualTo(missing.toString());
    }

    @Test
    void malformedJsonReturns400() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<JsonNode> response = rest.exchange(
                "/api/v1/wallets",
                HttpMethod.POST,
                new HttpEntity<>("{\"holderName\":".getBytes(StandardCharsets.UTF_8), headers),
                JsonNode.class
        );

        assertProblem(response, HttpStatus.BAD_REQUEST, "malformed-request");
    }

    @Test
    void unsupportedMediaTypeReturns415() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);

        ResponseEntity<JsonNode> response = rest.exchange(
                "/api/v1/wallets",
                HttpMethod.POST,
                new HttpEntity<>("not json", headers),
                JsonNode.class
        );

        assertProblem(response, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-media-type");
    }

    @Test
    void methodNotAllowedReturns405() {
        ResponseEntity<JsonNode> response = rest.exchange(
                "/api/v1/wallets",
                HttpMethod.PUT,
                new HttpEntity<>(new UpdateWalletRequest("Alice"), jsonHeaders()),
                JsonNode.class
        );

        assertProblem(response, HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed");
    }

    @Test
    void invalidPageParametersReturn400() {
        WalletResponse wallet = createWallet("Alice");

        ResponseEntity<JsonNode> tooLarge = rest.getForEntity(
                "/api/v1/wallets/" + wallet.id() + "/movements?page=0&size=101",
                JsonNode.class
        );
        assertProblem(tooLarge, HttpStatus.BAD_REQUEST, "validation-failed");

        ResponseEntity<JsonNode> negative = rest.getForEntity(
                "/api/v1/wallets/" + wallet.id() + "/movements?page=-1&size=20",
                JsonNode.class
        );
        assertProblem(negative, HttpStatus.BAD_REQUEST, "validation-failed");
    }

    @Test
    void unknownPathReturns404ProblemDetails() {
        ResponseEntity<JsonNode> response = rest.getForEntity(
                "/api/v1/does-not-exist",
                JsonNode.class
        );

        assertProblem(response, HttpStatus.NOT_FOUND, "resource-not-found");
    }
}
