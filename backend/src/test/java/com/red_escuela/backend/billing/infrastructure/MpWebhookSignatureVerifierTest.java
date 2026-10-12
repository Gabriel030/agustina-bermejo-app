package com.red_escuela.backend.billing.infrastructure;

import com.red_escuela.backend.billing.exception.InvalidMpWebhookSignatureException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

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

    private static void assertRejected(MpWebhookSignatureVerifier verifier, String signature, String requestId, String paymentId) {
        assertThatThrownBy(() -> verifier.verify(signature, requestId, paymentId)).isInstanceOf(InvalidMpWebhookSignatureException.class)
                .hasMessage("Firma de webhook inválida");
    }

    @Test
    void aBlankOrNullSecretIsTreatedAsNotConfigured() {
        assertThatThrownBy(() -> new MpWebhookSignatureVerifier("   ", false).verify("", "r", "1")).isInstanceOf(InvalidMpWebhookSignatureException.class)
                .hasMessageContaining("secreto");
        assertThatThrownBy(() -> new MpWebhookSignatureVerifier(null, false).verify("", "r", "1")).isInstanceOf(InvalidMpWebhookSignatureException.class)
                .hasMessageContaining("secreto");
        assertThatCode(() -> new MpWebhookSignatureVerifier(null, true).verify("", "r", "1")).doesNotThrowAnyException();
        assertThatCode(() -> new MpWebhookSignatureVerifier("   ", true).verify("", "r", "1")).doesNotThrowAnyException();
    }

    @Test
    void aConfiguredSecretIsEnforcedEvenWhenUnsignedWebhooksAreAllowed() {
        assertRejected(new MpWebhookSignatureVerifier(SECRET, true), "", "request-1", "123");
    }

    @Test
    void unsignedWebhooksAreOnlyAllowedUnderTheLocalProfile() {
        MockEnvironment local = new MockEnvironment();
        local.setActiveProfiles("local");

        assertThatCode(() -> new MpWebhookSignatureVerifier("", local).verify("", "r", "1")).doesNotThrowAnyException();
        assertThatThrownBy(() -> new MpWebhookSignatureVerifier("", new MockEnvironment()).verify("", "r", "1"))
                .isInstanceOf(InvalidMpWebhookSignatureException.class);
    }

    @Test
    void aSignatureMissingTheTimestampOrTheHashIsRejected() throws Exception {
        MpWebhookSignatureVerifier verifier = new MpWebhookSignatureVerifier(SECRET, false);
        String valid = sign(SECRET, "123", "request-1", "1700000000");
        String hash = valid.substring(valid.indexOf("v1="));

        assertRejected(verifier, "ts=1700000000", "request-1", "123");
        assertRejected(verifier, hash, "request-1", "123");
        assertRejected(verifier, "garbage", "request-1", "123");
    }

    @Test
    void theSignaturePartsMayComeInAnyOrderAndWithSpacesAroundTheComma() throws Exception {
        MpWebhookSignatureVerifier verifier = new MpWebhookSignatureVerifier(SECRET, false);
        String valid = sign(SECRET, "123", "request-1", "1700000000");
        String ts = valid.substring(0, valid.indexOf(","));
        String hash = valid.substring(valid.indexOf(",") + 1);

        assertThatCode(() -> verifier.verify(hash + "," + ts, "request-1", "123")).doesNotThrowAnyException();
        assertThatCode(() -> verifier.verify(ts + " , " + hash, "request-1", "123")).doesNotThrowAnyException();
    }

    @Test
    void aTamperedPaymentIdRequestIdOrTimestampIsRejected() throws Exception {
        MpWebhookSignatureVerifier verifier = new MpWebhookSignatureVerifier(SECRET, false);
        String valid = sign(SECRET, "123", "request-1", "1700000000");

        assertRejected(verifier, valid, "request-1", "124");
        assertRejected(verifier, valid, "request-2", "123");
        assertRejected(verifier, valid.replace("ts=1700000000", "ts=1700000001"), "request-1", "123");
    }

    @Test
    void aHashThatIsATruncatedOrExtendedOrUppercasedVersionOfTheRightOneIsRejected() throws Exception {
        MpWebhookSignatureVerifier verifier = new MpWebhookSignatureVerifier(SECRET, false);
        String valid = sign(SECRET, "123", "request-1", "1700000000");

        assertRejected(verifier, valid.substring(0, valid.length() - 1), "request-1", "123");
        assertRejected(verifier, valid + "0", "request-1", "123");
        assertRejected(verifier, valid.substring(0, valid.indexOf("v1=")) + valid.substring(valid.indexOf("v1=")).toUpperCase(), "request-1", "123");
    }

    @Test
    void aValueContainingAnEqualsSignKeepsItsWholeTextAndIsRejectedAsAMismatch() throws Exception {
        MpWebhookSignatureVerifier verifier = new MpWebhookSignatureVerifier(SECRET, false);
        String valid = sign(SECRET, "123", "request-1", "1700000000");

        assertRejected(verifier, valid + "=", "request-1", "123");
    }
}
