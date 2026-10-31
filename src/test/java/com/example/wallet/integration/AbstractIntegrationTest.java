package com.example.wallet.integration;

import com.example.wallet.api.dto.AmountRequest;
import com.example.wallet.api.dto.CreateWalletRequest;
import com.example.wallet.api.dto.MovementResponse;
import com.example.wallet.api.dto.PageResponse;
import com.example.wallet.api.dto.TransferRequest;
import com.example.wallet.api.dto.TransferResponse;
import com.example.wallet.api.dto.WalletOperationResponse;
import com.example.wallet.api.dto.WalletResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("wallet")
            .withUsername("wallet")
            .withPassword("wallet");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ObjectMapper objectMapper;

    @org.junit.jupiter.api.BeforeEach
    void resetDatabase() {
        jdbc.execute("TRUNCATE TABLE wallet_movements, idempotency_records, wallets RESTART IDENTITY CASCADE");
    }

    protected WalletResponse createWallet(String holderName) {
        ResponseEntity<WalletResponse> response = rest.postForEntity(
                "/api/v1/wallets",
                new CreateWalletRequest(holderName),
                WalletResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    protected WalletResponse getWallet(UUID walletId) {
        ResponseEntity<WalletResponse> response = rest.getForEntity(
                "/api/v1/wallets/" + walletId,
                WalletResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    protected ResponseEntity<JsonNode> depositRaw(UUID walletId, String amount, String idempotencyKey) {
        return rest.exchange(
                "/api/v1/wallets/" + walletId + "/deposits",
                HttpMethod.POST,
                new HttpEntity<>(new AmountRequest(new BigDecimal(amount)), idempotencyHeaders(idempotencyKey)),
                JsonNode.class
        );
    }

    protected ResponseEntity<JsonNode> withdrawRaw(UUID walletId, String amount, String idempotencyKey) {
        return rest.exchange(
                "/api/v1/wallets/" + walletId + "/withdrawals",
                HttpMethod.POST,
                new HttpEntity<>(new AmountRequest(new BigDecimal(amount)), idempotencyHeaders(idempotencyKey)),
                JsonNode.class
        );
    }

    protected ResponseEntity<JsonNode> transferRaw(UUID sourceWalletId,
                                                   UUID targetWalletId,
                                                   String amount,
                                                   String idempotencyKey) {
        return rest.exchange(
                "/api/v1/transfers",
                HttpMethod.POST,
                new HttpEntity<>(
                        new TransferRequest(sourceWalletId, targetWalletId, new BigDecimal(amount)),
                        idempotencyHeaders(idempotencyKey)
                ),
                JsonNode.class
        );
    }

    protected WalletOperationResponse depositOk(UUID walletId, String amount, String idempotencyKey) {
        ResponseEntity<JsonNode> response = depositRaw(walletId, amount, idempotencyKey);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return read(response.getBody(), WalletOperationResponse.class);
    }

    protected WalletOperationResponse withdrawOk(UUID walletId, String amount, String idempotencyKey) {
        ResponseEntity<JsonNode> response = withdrawRaw(walletId, amount, idempotencyKey);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return read(response.getBody(), WalletOperationResponse.class);
    }

    protected TransferResponse transferOk(UUID sourceWalletId,
                                          UUID targetWalletId,
                                          String amount,
                                          String idempotencyKey) {
        ResponseEntity<JsonNode> response = transferRaw(sourceWalletId, targetWalletId, amount, idempotencyKey);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return read(response.getBody(), TransferResponse.class);
    }

    protected ResponseEntity<PageResponse<MovementResponse>> movements(UUID walletId, int page, int size) {
        return rest.exchange(
                "/api/v1/wallets/" + walletId + "/movements?page=" + page + "&size=" + size,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<PageResponse<MovementResponse>>() {
                }
        );
    }

    protected HttpHeaders idempotencyHeaders(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        return headers;
    }

    protected HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    protected <T> T read(JsonNode body, Class<T> type) {
        assertThat(body).isNotNull();
        try {
            return objectMapper.treeToValue(body, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not read response body", ex);
        }
    }

    protected JsonNode assertProblem(ResponseEntity<JsonNode> response, HttpStatus status, String typeSlug) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        MediaType contentType = response.getHeaders().getContentType();
        assertThat(contentType).isNotNull();
        assertThat(contentType.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();

        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("status").asInt()).isEqualTo(status.value());
        assertThat(body.path("type").asText()).isEqualTo("https://wallet.example/problems/" + typeSlug);
        assertThat(body.path("title").asText()).isNotBlank();
        assertThat(body.path("detail").asText()).isNotBlank();
        assertThat(body.path("instance").asText()).isNotBlank();
        return body;
    }

    protected BigDecimal balanceInDb(UUID walletId) {
        BigDecimal balance = jdbc.queryForObject(
                "select balance from wallets where id = ?",
                BigDecimal.class,
                walletId
        );
        assertThat(balance).isNotNull();
        return balance;
    }

    protected int countMovements(UUID walletId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from wallet_movements where wallet_id = ?",
                Integer.class,
                walletId
        );
        return count == null ? 0 : count;
    }

    protected int idempotencyRecordCount() {
        Integer count = jdbc.queryForObject("select count(*) from idempotency_records", Integer.class);
        return count == null ? 0 : count;
    }
}
