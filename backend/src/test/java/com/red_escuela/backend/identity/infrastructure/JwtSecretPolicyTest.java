package com.red_escuela.backend.identity.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSecretPolicyTest {

    @Test
    void secretShorterThan32CharactersIsRejected() {
        assertThatThrownBy(() -> JwtSecretPolicy.requireStrong("a".repeat(31)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void secretOf32CharactersIsAccepted() {
        String secret = "aB3dE5gH7jK9mN1pQ3sT5vX7zA9cD1fG";

        assertThat(JwtSecretPolicy.requireStrong(secret)).isEqualTo(secret);
    }

    @Test
    void secretWithFewDistinctCharactersIsRejected() {
        assertThatThrownBy(() -> JwtSecretPolicy.requireStrong("a".repeat(48)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> JwtSecretPolicy.requireStrong("abababababababababababababababab"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nullSecretIsRejected() {
        assertThatThrownBy(() -> JwtSecretPolicy.requireStrong(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void jwtServiceRefusesToBuildWithShortSecret() {
        assertThatThrownBy(() -> new JwtService("short", 15, "agustina-bermejo-app", "agustina-bermejo-app-api", false))
                .isInstanceOf(IllegalStateException.class);
    }
}
