package com.red_escuela.backend.billing.infrastructure;

import com.red_escuela.backend.billing.exception.InvalidMpWebhookSignatureException;
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
import java.util.HexFormat;

@Component
public class MpWebhookSignatureVerifier {
    private final String secret;
    private final boolean unsignedAllowed;

    @Autowired
    public MpWebhookSignatureVerifier(@Value("${mercadopago.webhook-secret:}") String secret, Environment environment) {
        this(secret, environment.acceptsProfiles(Profiles.of("local")));
    }

    public MpWebhookSignatureVerifier(String secret, boolean unsignedAllowed) {
        this.secret = secret;
        this.unsignedAllowed = unsignedAllowed;
    }

    public void verify(String xSignature, String xRequestId, String mpPaymentId) {
        if (secret == null || secret.isBlank()) {
            if (unsignedAllowed) {
                return;
            }
            throw new InvalidMpWebhookSignatureException("No hay secreto de webhook configurado: se rechaza el webhook");
        }
        String ts = null;
        String v1 = null;
        for (String part : xSignature.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2) {
                if ("ts".equals(kv[0])) ts = kv[1];
                else if ("v1".equals(kv[0])) v1 = kv[1];
            }
        }
        if (ts == null || v1 == null) {
            throw new InvalidMpWebhookSignatureException("Firma de webhook inválida");
        }
        String template = "id:" + mpPaymentId + ";request-id:" + xRequestId + ";ts:" + ts + ";";
        byte[] expected = HexFormat.of().formatHex(hmacSha256(template)).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, v1.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidMpWebhookSignatureException("Firma de webhook inválida");
        }
    }

    private byte[] hmacSha256(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new InvalidMpWebhookSignatureException("Error al validar firma de webhook", e);
        }
    }
}
