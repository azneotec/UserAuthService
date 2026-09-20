package com.azneotech.userauthservice.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHasherTest {

    @Test
    void sha256Hex_matchesKnownVector() {
        // SHA-256("abc") from FIPS 180-4
        assertThat(TokenHasher.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void sha256Hex_isAlways64LowercaseHexChars() {
        assertThat(TokenHasher.sha256Hex("")).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(TokenHasher.sha256Hex("some.jwt.token")).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void sha256Hex_isDeterministicAndInputSensitive() {
        assertThat(TokenHasher.sha256Hex("token")).isEqualTo(TokenHasher.sha256Hex("token"));
        assertThat(TokenHasher.sha256Hex("token")).isNotEqualTo(TokenHasher.sha256Hex("token "));
    }

}
