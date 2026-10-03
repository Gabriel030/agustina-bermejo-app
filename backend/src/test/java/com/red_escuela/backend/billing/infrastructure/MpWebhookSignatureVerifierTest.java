package com.red_escuela.backend.billing.infrastructure;

import com.red_escuela.backend.billing.exception.InvalidMpWebhookSignatureException;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MpWebhookSignatureVerifierTest {
    private static final String SECRET = "webhook-secret";

    private static String sign(String secret, String paymentId, String requestId, String ts) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String template = "id:" + paymentId + ";request-id:" + requestId + ";ts:" + ts + ";";
        return "ts=" + ts + ",v1=" + HexFormat.of().formatHex(mac.doFinal(template.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void withoutAConfiguredSecretTheWebhookIsRejectedOutsideLocal() {
        MpWebhookSignatureVerifier verifier = new MpWebhookSignatureVerifier("", false);

        assertThatThrownBy(() -> verifier.verify("", "request-1", "123"))
                .isInstanceOf(InvalidMpWebhookSignatureException.class);
    }

    @Test
    void withoutAConfiguredSecretTheWebhookIsAcceptedInLocal() {
        MpWebhookSignatureVerifier verifier = new MpWebhookSignatureVerifier("", true);

        assertThatCode(() -> verifier.verify("", "request-1", "123")).doesNotThrowAnyException();
    }

    @Test
    void aCorrectlySignedWebhookIsAccepted() throws Exception {
        MpWebhookSignatureVerifier verifier = new MpWebhookSignatureVerifier(SECRET, false);

        assertThatCode(() -> verifier.verify(sign(SECRET, "123", "request-1", "1700000000"), "request-1", "123"))
                .doesNotThrowAnyException();
    }

    @Test
    void aWebhookSignedWithAnotherSecretIsRejected() throws Exception {
        MpWebhookSignatureVerifier verifier = new MpWebhookSignatureVerifier(SECRET, false);

        assertThatThrownBy(() -> verifier.verify(sign("other-secret", "123", "request-1", "1700000000"), "request-1", "123"))
                .isInstanceOf(InvalidMpWebhookSignatureException.class);
    }

    @Test
    void aWebhookWithoutSignatureIsRejectedWhenASecretIsConfigured() {
        MpWebhookSignatureVerifier verifier = new MpWebhookSignatureVerifier(SECRET, false);

        assertThatThrownBy(() -> verifier.verify("", "request-1", "123"))
                .isInstanceOf(InvalidMpWebhookSignatureException.class);
    }
}
