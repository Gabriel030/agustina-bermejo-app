package com.red_escuela.backend.billing.infrastructure;

import com.red_escuela.backend.billing.exception.InvalidMpWebhookSignatureException;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MpWebhookSignatureVerifierFreshnessTest {
    private static final String SECRET = "webhook-secret";
    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration MAX_AGE = Duration.ofMinutes(5);

    private static String sign(String timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String template = "id:123;request-id:request-1;ts:" + timestamp + ";";
        return "ts=" + timestamp + ",v1=" + HexFormat.of().formatHex(mac.doFinal(template.getBytes(StandardCharsets.UTF_8)));
    }

    private static String millisBefore(Duration age) {
        return String.valueOf(NOW.minus(age).toEpochMilli());
    }

    private final MpWebhookSignatureVerifier verifier = new MpWebhookSignatureVerifier(SECRET, false, CLOCK, MAX_AGE);

    @Test
    void signatureWithTimestampOlderThanMaxAgeIsRejected() throws Exception {
        String signature = sign(millisBefore(Duration.ofMinutes(10)));

        assertThatThrownBy(() -> verifier.verify(signature, "request-1", "123"))
                .isInstanceOf(InvalidMpWebhookSignatureException.class);
    }

    @Test
    void signatureJustPastMaxAgeIsRejectedAndJustInsideIsAccepted() throws Exception {
        String pastLimit = sign(millisBefore(MAX_AGE.plusMillis(1)));
        String insideLimit = sign(millisBefore(MAX_AGE));

        assertThatThrownBy(() -> verifier.verify(pastLimit, "request-1", "123"))
                .isInstanceOf(InvalidMpWebhookSignatureException.class);
        assertThatCode(() -> verifier.verify(insideLimit, "request-1", "123")).doesNotThrowAnyException();
    }

    @Test
    void signatureWithinMaxAgeIsAccepted() throws Exception {
        String signature = sign(millisBefore(Duration.ofMinutes(1)));

        assertThatCode(() -> verifier.verify(signature, "request-1", "123")).doesNotThrowAnyException();
    }

    @Test
    void signatureWithTimestampFarInTheFutureIsRejected() throws Exception {
        String signature = sign(String.valueOf(NOW.plus(Duration.ofMinutes(10)).toEpochMilli()));

        assertThatThrownBy(() -> verifier.verify(signature, "request-1", "123"))
                .isInstanceOf(InvalidMpWebhookSignatureException.class);
    }

    @Test
    void nonNumericTimestampIsRejectedEvenWithValidHmac() throws Exception {
        String signature = sign("yesterday");

        assertThatThrownBy(() -> verifier.verify(signature, "request-1", "123"))
                .isInstanceOf(InvalidMpWebhookSignatureException.class);
    }

    @Test
    void staleTimestampWithForgedHmacIsRejectedAsInvalidSignatureNotAsExpired() {
        String forged = "ts=" + millisBefore(Duration.ofHours(1)) + ",v1=deadbeef";

        assertThatThrownBy(() -> verifier.verify(forged, "request-1", "123"))
                .isInstanceOf(InvalidMpWebhookSignatureException.class)
                .hasMessage("Firma de webhook inválida");
    }

    @Test
    void unsignedModeSkipsFreshnessCheck() {
        MpWebhookSignatureVerifier unsigned = new MpWebhookSignatureVerifier("", true, CLOCK, MAX_AGE);

        assertThatCode(() -> unsigned.verify("ts=1,v1=abc", "request-1", "123")).doesNotThrowAnyException();
    }
}
