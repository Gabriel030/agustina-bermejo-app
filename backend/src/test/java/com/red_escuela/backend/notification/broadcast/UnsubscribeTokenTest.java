package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ConsentChannel;
import com.red_escuela.backend.notification.broadcast.domain.UnsubscribeClaims;
import com.red_escuela.backend.notification.broadcast.exception.InvalidUnsubscribeTokenException;
import com.red_escuela.backend.notification.broadcast.specialist.UnsubscribeTokenIssuer;
import com.red_escuela.backend.notification.broadcast.specialist.UnsubscribeTokenSigner;
import com.red_escuela.backend.notification.broadcast.specialist.UnsubscribeTokenVerifier;
import org.junit.jupiter.api.Test;

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
}
