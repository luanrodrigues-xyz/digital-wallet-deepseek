package com.example.wallet.integration;

import com.example.wallet.api.dto.MovementResponse;
import com.example.wallet.api.dto.PageResponse;
import com.example.wallet.api.dto.UpdateWalletRequest;
import com.example.wallet.api.dto.WalletResponse;
import com.example.wallet.domain.WalletStatus;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class WalletApiIntegrationTest extends AbstractIntegrationTest {

    @Test
    void openApiDocumentsAllOperations() {
        var response = rest.getForEntity("/v3/api-docs", com.fasterxml.jackson.databind.JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        var paths = response.getBody().path("paths");
        assertThat(paths.has("/api/v1/wallets")).isTrue();
        assertThat(paths.has("/api/v1/wallets/{walletId}/deposits")).isTrue();
        assertThat(paths.has("/api/v1/wallets/{walletId}/withdrawals")).isTrue();
        assertThat(paths.has("/api/v1/transfers")).isTrue();
        assertThat(paths.has("/api/v1/wallets/{walletId}/movements")).isTrue();
    }

    @Test
    void createGetAndListWallets() {
        ResponseEntity<WalletResponse> created = rest.postForEntity(
                "/api/v1/wallets",
                new com.example.wallet.api.dto.CreateWalletRequest("Alice"),
                WalletResponse.class
        );

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation()).isNotNull();
        WalletResponse body = created.getBody();
        assertThat(body).isNotNull();
        assertThat(body.id()).isNotNull();
        assertThat(body.holderName()).isEqualTo("Alice");
        assertThat(body.balance()).isEqualByComparingTo("0.00");
        assertThat(body.status()).isEqualTo(WalletStatus.ACTIVE);
        assertThat(created.getHeaders().getLocation().getPath())
                .isEqualTo("/api/v1/wallets/" + body.id());

        WalletResponse fetched = getWallet(body.id());
        assertThat(fetched).isEqualTo(body);

        createWallet("Bob");
        createWallet("Carol");

        ResponseEntity<PageResponse<WalletResponse>> page = rest.exchange(
                "/api/v1/wallets?page=0&size=2",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<PageResponse<WalletResponse>>() {
                }
        );

        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).isNotNull();
        assertThat(page.getBody().totalElements()).isEqualTo(3);
        assertThat(page.getBody().totalPages()).isEqualTo(2);
        assertThat(page.getBody().content()).hasSize(2);
        assertThat(page.getBody().first()).isTrue();
        assertThat(page.getBody().last()).isFalse();
    }

    @Test
    void updateHolderName() {
        WalletResponse wallet = createWallet("Alice");

        ResponseEntity<WalletResponse> updated = rest.exchange(
                "/api/v1/wallets/" + wallet.id(),
                HttpMethod.PUT,
                new HttpEntity<>(new UpdateWalletRequest("  Bob  "), jsonHeaders()),
                WalletResponse.class
        );

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).isNotNull();
        assertThat(updated.getBody().holderName()).isEqualTo("Bob");
        assertThat(getWallet(wallet.id()).holderName()).isEqualTo("Bob");
        assertThat(getWallet(wallet.id())).isEqualTo(updated.getBody());
    }

    @Test
    void deactivateIsIdempotentAndHistoryRemainsReadable() {
        WalletResponse wallet = createWallet("Alice");
        depositOk(wallet.id(), "10.00", "seed-key");

        ResponseEntity<WalletResponse> first = rest.exchange(
                "/api/v1/wallets/" + wallet.id(),
                HttpMethod.DELETE,
                null,
                WalletResponse.class
        );
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).isNotNull();
        assertThat(first.getBody().status()).isEqualTo(WalletStatus.INACTIVE);

        ResponseEntity<WalletResponse> second = rest.exchange(
                "/api/v1/wallets/" + wallet.id(),
                HttpMethod.DELETE,
                null,
                WalletResponse.class
        );
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody()).isNotNull();
        assertThat(second.getBody().status()).isEqualTo(WalletStatus.INACTIVE);

        ResponseEntity<PageResponse<MovementResponse>> history = movements(wallet.id(), 0, 20);
        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(history.getBody()).isNotNull();
        assertThat(history.getBody().totalElements()).isEqualTo(1);
    }

    @Test
    void listCanFilterByStatus() {
        WalletResponse active = createWallet("Active");
        WalletResponse inactive = createWallet("Inactive");
        rest.exchange(
                "/api/v1/wallets/" + inactive.id(),
                HttpMethod.DELETE,
                null,
                WalletResponse.class
        );

        ResponseEntity<PageResponse<WalletResponse>> activePage = rest.exchange(
                "/api/v1/wallets?status=ACTIVE&page=0&size=20",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<PageResponse<WalletResponse>>() {
                }
        );

        assertThat(activePage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(activePage.getBody()).isNotNull();
        assertThat(activePage.getBody().totalElements()).isEqualTo(1);
        assertThat(activePage.getBody().content().getFirst().id()).isEqualTo(active.id());
    }
}
