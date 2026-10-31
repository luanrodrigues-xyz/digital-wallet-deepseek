package com.example.wallet.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestHasherTest {

    @Test
    void producesKnownSha256Hex() {
        assertThat(RequestHasher.sha256("hello"))
                .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    void sameInputProducesSameHashAndDifferentInputProducesDifferentHash() {
        assertThat(RequestHasher.sha256("a")).isEqualTo(RequestHasher.sha256("a"));
        assertThat(RequestHasher.sha256("a")).isNotEqualTo(RequestHasher.sha256("b"));
        assertThat(RequestHasher.sha256("a")).hasSize(64);
    }
}
