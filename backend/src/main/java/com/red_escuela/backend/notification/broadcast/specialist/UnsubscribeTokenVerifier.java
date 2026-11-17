package com.red_escuela.backend.notification.broadcast.specialist;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ConsentChannel;
import com.red_escuela.backend.notification.broadcast.domain.UnsubscribeClaims;
import com.red_escuela.backend.notification.broadcast.exception.InvalidUnsubscribeTokenException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Component
public class UnsubscribeTokenVerifier {

    private static final int FIELD_COUNT = 4;

    private final UnsubscribeTokenSigner signer;

    public UnsubscribeTokenVerifier(UnsubscribeTokenSigner signer) {
        this.signer = signer;
    }

    public UnsubscribeClaims verifyToken(String token) {
        try {
            String[] parts = token.split("\\" + UnsubscribeTokenIssuer.SIGNATURE_SEPARATOR, -1);
            String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            if (parts.length != 2 || !signer.matches(payload, parts[1])) {
                throw new InvalidUnsubscribeTokenException();
            }
            String[] fields = payload.split("\\" + UnsubscribeTokenIssuer.FIELD_SEPARATOR, -1);
            if (fields.length != FIELD_COUNT) {
                throw new InvalidUnsubscribeTokenException();
            }
            return new UnsubscribeClaims(Long.valueOf(fields[0]), ConsentChannel.valueOf(fields[1]),
                    BroadcastCategory.valueOf(fields[2]), Instant.ofEpochSecond(Long.parseLong(fields[3])));
        } catch (IllegalArgumentException | NullPointerException malformed) {
            throw new InvalidUnsubscribeTokenException();
        }
    }
}
