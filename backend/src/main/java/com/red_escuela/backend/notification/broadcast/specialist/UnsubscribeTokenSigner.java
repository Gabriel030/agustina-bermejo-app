package com.red_escuela.backend.notification.broadcast.specialist;

import org.springframework.beans.factory.annotation.Value;
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

    public UnsubscribeTokenSigner(@Value("${broadcast.unsubscribe.secret:${jwt.secret}}") String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
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
