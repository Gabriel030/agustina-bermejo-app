package com.red_escuela.backend.identity.infrastructure;

import com.red_escuela.backend.config.JwtSecretPolicy;
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
        String secret = "a".repeat(32);

        assertThat(JwtSecretPolicy.requireStrong(secret)).isEqualTo(secret);
    }

    @Test
    void nullSecretIsRejected() {
        assertThatThrownBy(() -> JwtSecretPolicy.requireStrong(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void jwtServiceRefusesToBuildWithShortSecret() {
        assertThatThrownBy(() -> new JwtService("short", 15))
                .isInstanceOf(IllegalStateException.class);
    }
}
