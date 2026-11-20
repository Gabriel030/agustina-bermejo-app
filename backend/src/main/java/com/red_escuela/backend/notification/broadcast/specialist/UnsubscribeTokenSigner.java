package com.red_escuela.backend.notification.broadcast.specialist;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class UnsubscribeTokenSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    @Autowired
    public UnsubscribeTokenSigner(@Value("${broadcast.unsubscribe.secret:}") String ownSecret,
                                  @Value("${jwt.secret}") String jwtSecret, Environment environment) {
        this(resolveSecret(ownSecret, jwtSecret, environment.acceptsProfiles(Profiles.of("prod"))));
    }

    public UnsubscribeTokenSigner(String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    private static String resolveSecret(String ownSecret, String jwtSecret, boolean production) {
        if (ownSecret != null && !ownSecret.isBlank()) {
            return ownSecret;
        }
        if (production) {
            throw new IllegalStateException("broadcast.unsubscribe.secret es obligatorio en el perfil prod");
        }
        return jwtSecret;
    }

    String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo firmar el token de baja", e);
        }
    }

    boolean matches(String payload, String presentedSignature) {
        return MessageDigest.isEqual(
                presentedSignature.getBytes(StandardCharsets.UTF_8),
                sign(payload).getBytes(StandardCharsets.UTF_8));
    }
}
