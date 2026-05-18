package com.chunbaetour.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    private final PasswordHasher passwordHasher = new PasswordHasher();

    @Test
    void hash_result_matches_original_password() {
        String raw = "Pa$$w0rd1!";
        String hashed = passwordHasher.hash(raw);

        assertThat(passwordHasher.matches(raw, hashed)).isTrue();
    }

    @Test
    void hash_result_does_not_match_different_password() {
        String hashed = passwordHasher.hash("Pa$$w0rd1!");

        assertThat(passwordHasher.matches("Different1!", hashed)).isFalse();
    }

    @Test
    void same_input_produces_different_hashes() {
        String raw = "Pa$$w0rd1!";

        String first = passwordHasher.hash(raw);
        String second = passwordHasher.hash(raw);

        assertThat(first).isNotEqualTo(second);
        assertThat(passwordHasher.matches(raw, first)).isTrue();
        assertThat(passwordHasher.matches(raw, second)).isTrue();
    }
}
