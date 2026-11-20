package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ConsentChannel;
import com.red_escuela.backend.notification.broadcast.domain.UnsubscribeClaims;
import com.red_escuela.backend.notification.broadcast.exception.InvalidUnsubscribeTokenException;
import com.red_escuela.backend.notification.broadcast.specialist.UnsubscribeTokenIssuer;
import com.red_escuela.backend.notification.broadcast.specialist.UnsubscribeTokenSigner;
import com.red_escuela.backend.notification.broadcast.specialist.UnsubscribeTokenVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnsubscribeTokenTest {

    private static final Instant ISSUED_AT = Instant.parse("2020-01-15T10:00:00Z");

    private final UnsubscribeTokenSigner signer = new UnsubscribeTokenSigner("unit-test-secret-unit-test-secret");
    private final UnsubscribeTokenIssuer issuer = new UnsubscribeTokenIssuer(signer, Clock.fixed(ISSUED_AT, ZoneOffset.UTC));
    private final UnsubscribeTokenVerifier verifier = new UnsubscribeTokenVerifier(signer);

    @Test
    void roundTripsClaims() {
        String token = issuer.issueToken(77L, ConsentChannel.WHATSAPP, BroadcastCategory.PROMOTIONAL);

        assertThat(verifier.verifyToken(token))
                .isEqualTo(new UnsubscribeClaims(77L, ConsentChannel.WHATSAPP, BroadcastCategory.PROMOTIONAL, ISSUED_AT));
    }

    @Test
    void acceptsOldTokenWithValidSignature() {
        String token = issuer.issueToken(5L, ConsentChannel.EMAIL, BroadcastCategory.INSTITUTIONAL);

        assertThat(new UnsubscribeTokenVerifier(signer).verifyToken(token).issuedAt()).isEqualTo(ISSUED_AT);
    }

    @Test
    void rejectsTamperedSignature() {
        String token = issuer.issueToken(5L, ConsentChannel.EMAIL, BroadcastCategory.PROMOTIONAL);
        String other = issuer.issueToken(6L, ConsentChannel.EMAIL, BroadcastCategory.PROMOTIONAL);
        String forgedPayload = other.substring(0, other.indexOf('.')) + token.substring(token.indexOf('.'));
        String flippedSignature = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

        assertThatThrownBy(() -> verifier.verifyToken(forgedPayload)).isInstanceOf(InvalidUnsubscribeTokenException.class);
        assertThatThrownBy(() -> verifier.verifyToken(flippedSignature)).isInstanceOf(InvalidUnsubscribeTokenException.class);
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        String foreign = new UnsubscribeTokenIssuer(new UnsubscribeTokenSigner("another-secret-another-secret-12"),
                Clock.fixed(ISSUED_AT, ZoneOffset.UTC)).issueToken(5L, ConsentChannel.EMAIL, BroadcastCategory.PROMOTIONAL);

        assertThatThrownBy(() -> verifier.verifyToken(foreign)).isInstanceOf(InvalidUnsubscribeTokenException.class);
    }

    @Test
    void rejectsMalformedToken() {
        for (String malformed : new String[]{"", "abc", "a.b.c", ".", "!!!.???", "YQ.YQ"}) {
            assertThatThrownBy(() -> verifier.verifyToken(malformed))
                    .as(malformed)
                    .isInstanceOf(InvalidUnsubscribeTokenException.class);
        }
    }

    private static UnsubscribeTokenSigner signerFor(String ownSecret, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new UnsubscribeTokenSigner(ownSecret, "jwt-secret-jwt-secret-jwt-secret-12", environment);
    }

    @Test
    void productionWithoutOwnSecretFailsToStart() {
        assertThatThrownBy(() -> signerFor("", "prod")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> signerFor("  ", "prod")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void productionUsesOwnSecretNotTheJwtOne() {
        UnsubscribeTokenSigner production = signerFor("own-secret-own-secret-own-secret-1", "prod");
        String token = new UnsubscribeTokenIssuer(production, Clock.fixed(ISSUED_AT, ZoneOffset.UTC))
                .issueToken(5L, ConsentChannel.EMAIL, BroadcastCategory.PROMOTIONAL);

        assertThat(new UnsubscribeTokenVerifier(production).verifyToken(token).personId()).isEqualTo(5L);
        assertThatThrownBy(() -> new UnsubscribeTokenVerifier(new UnsubscribeTokenSigner("jwt-secret-jwt-secret-jwt-secret-12")).verifyToken(token))
                .isInstanceOf(InvalidUnsubscribeTokenException.class);
    }

    @Test
    void outsideProductionFallsBackToTheJwtSecret() {
        String token = new UnsubscribeTokenIssuer(signerFor("", "local"), Clock.fixed(ISSUED_AT, ZoneOffset.UTC))
                .issueToken(5L, ConsentChannel.EMAIL, BroadcastCategory.PROMOTIONAL);

        assertThat(new UnsubscribeTokenVerifier(new UnsubscribeTokenSigner("jwt-secret-jwt-secret-jwt-secret-12")).verifyToken(token).personId())
                .isEqualTo(5L);
    }

    @Test
    void springUsesTheConstructorThatKnowsTheProfile() {
        var autowired = java.util.Arrays.stream(UnsubscribeTokenSigner.class.getConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(org.springframework.beans.factory.annotation.Autowired.class))
                .toList();

        assertThat(autowired).hasSize(1);
        assertThat(autowired.get(0).getParameterTypes()).containsExactly(String.class, String.class, org.springframework.core.env.Environment.class);
    }
}
