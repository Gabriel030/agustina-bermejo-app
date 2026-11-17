package com.red_escuela.backend.notification.broadcast.specialist;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ConsentChannel;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;

@Component
public class UnsubscribeTokenIssuer {

    static final String FIELD_SEPARATOR = "|";
    static final String SIGNATURE_SEPARATOR = ".";

    private final UnsubscribeTokenSigner signer;
    private final Clock clock;

    public UnsubscribeTokenIssuer(UnsubscribeTokenSigner signer, Clock clock) {
        this.signer = signer;
        this.clock = clock;
    }

    public String issueToken(Long personId, ConsentChannel channel, BroadcastCategory category) {
        String payload = String.join(FIELD_SEPARATOR,
                personId.toString(), channel.name(), category.name(), Long.toString(clock.instant().getEpochSecond()));
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + SIGNATURE_SEPARATOR + signer.sign(payload);
    }
}
