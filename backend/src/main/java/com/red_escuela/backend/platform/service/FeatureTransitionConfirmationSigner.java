package com.red_escuela.backend.platform.service;

import com.red_escuela.backend.platform.domain.IssuedTransitionConfirmation;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.platform.model.FeatureCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;

@Component
public class FeatureTransitionConfirmationSigner {

    public static final Duration VALIDITY = Duration.ofMinutes(15);

    private static final String ALGORITHM = "HmacSHA256";
    private static final String SEPARATOR = ".";

    private final SecretKeySpec key;
    private final Clock clock;

    public FeatureTransitionConfirmationSigner(
            @Value("${platform.feature-confirmation.secret:${jwt.secret}}") String secret,
            Clock clock) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        this.clock = clock;
    }

    public IssuedTransitionConfirmation issueConfirmation(FeatureCode code, BillingMode from, BillingMode to, long featureVersion) {
        Instant expiresAt = clock.instant().plus(VALIDITY);
        long expiresAtSeconds = expiresAt.getEpochSecond();
        String token = expiresAtSeconds + SEPARATOR + signature(code, from, to, featureVersion, expiresAtSeconds);
        return new IssuedTransitionConfirmation(token, LocalDateTime.now(clock).plus(VALIDITY));
    }

    public boolean isConfirmationValid(String token, FeatureCode code, BillingMode from, BillingMode to, long featureVersion) {
        if (token == null) {
            return false;
        }
        int separatorIndex = token.indexOf(SEPARATOR);
        if (separatorIndex <= 0) {
            return false;
        }
        long expiresAtSeconds;
        try {
            expiresAtSeconds = Long.parseLong(token.substring(0, separatorIndex));
        } catch (NumberFormatException notANumber) {
            return false;
        }
        if (clock.instant().getEpochSecond() > expiresAtSeconds) {
            return false;
        }
        byte[] presented = token.substring(separatorIndex + 1).getBytes(StandardCharsets.UTF_8);
        byte[] expected = signature(code, from, to, featureVersion, expiresAtSeconds).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(presented, expected);
    }

    private String signature(FeatureCode code, BillingMode from, BillingMode to, long featureVersion, long expiresAtSeconds) {
        String payload = String.join("|", code.name(), from.name(), to.name(), Long.toString(featureVersion), Long.toString(expiresAtSeconds));
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo firmar la confirmación de la transición", e);
        }
    }
}
