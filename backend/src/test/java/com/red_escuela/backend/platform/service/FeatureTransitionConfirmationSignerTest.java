package com.red_escuela.backend.platform.service;

import com.red_escuela.backend.platform.domain.IssuedTransitionConfirmation;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.platform.model.FeatureCode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureTransitionConfirmationSignerTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-10-31T12:00:00Z");
    private static final String SECRET = "secret-for-the-signer-tests-0123456789";

    private final FeatureTransitionConfirmationSigner signer = signerAt(ISSUED_AT, SECRET);

    @Test
    void aFreshConfirmationIsValidForTheSameTransitionAndVersion() {
        IssuedTransitionConfirmation confirmation = signer.issueConfirmation(FeatureCode.BILLING, BillingMode.FULL, BillingMode.OFF, 4);

        assertThat(signer.isConfirmationValid(confirmation.token(), FeatureCode.BILLING, BillingMode.FULL, BillingMode.OFF, 4)).isTrue();
    }

    @Test
    void theConfirmationReportsItsExpiryFifteenMinutesAhead() {
        IssuedTransitionConfirmation confirmation = signer.issueConfirmation(FeatureCode.BILLING, BillingMode.FULL, BillingMode.OFF, 4);

        assertThat(confirmation.expiresAt()).isEqualTo(java.time.LocalDateTime.ofInstant(ISSUED_AT.plus(Duration.ofMinutes(15)), ZoneOffset.UTC));
    }

    @Test
    void aConfirmationForAnotherTargetModeIsRejected() {
        IssuedTransitionConfirmation confirmation = signer.issueConfirmation(FeatureCode.BILLING, BillingMode.FULL, BillingMode.OFF, 4);

        assertThat(signer.isConfirmationValid(confirmation.token(), FeatureCode.BILLING, BillingMode.FULL, BillingMode.MIRROR, 4)).isFalse();
    }

    @Test
    void aConfirmationForAnotherSourceModeIsRejected() {
        IssuedTransitionConfirmation confirmation = signer.issueConfirmation(FeatureCode.BILLING, BillingMode.FULL, BillingMode.OFF, 4);

        assertThat(signer.isConfirmationValid(confirmation.token(), FeatureCode.BILLING, BillingMode.MIRROR, BillingMode.OFF, 4)).isFalse();
    }

    @Test
    void aConfirmationForAnotherFeatureVersionIsRejected() {
        IssuedTransitionConfirmation confirmation = signer.issueConfirmation(FeatureCode.BILLING, BillingMode.FULL, BillingMode.OFF, 4);

        assertThat(signer.isConfirmationValid(confirmation.token(), FeatureCode.BILLING, BillingMode.FULL, BillingMode.OFF, 5)).isFalse();
    }

    @Test
    void aConfirmationIsAcceptedUpToItsExpiryAndRejectedOneSecondAfter() {
        IssuedTransitionConfirmation confirmation = signer.issueConfirmation(FeatureCode.BILLING, BillingMode.FULL, BillingMode.OFF, 4);
        Instant expiry = ISSUED_AT.plus(FeatureTransitionConfirmationSigner.VALIDITY);

        assertThat(signerAt(expiry, SECRET).isConfirmationValid(confirmation.token(), FeatureCode.BILLING, BillingMode.FULL, BillingMode.OFF, 4)).isTrue();
        assertThat(signerAt(expiry.plusSeconds(1), SECRET).isConfirmationValid(confirmation.token(), FeatureCode.BILLING, BillingMode.FULL, BillingMode.OFF, 4)).isFalse();
    }

    @Test
    void aConfirmationSignedWithAnotherSecretIsRejected() {
        IssuedTransitionConfirmation foreign = signerAt(ISSUED_AT, "another-secret-another-secret-0123456789")
                .issueConfirmation(FeatureCode.BILLING, BillingMode.FULL, BillingMode.OFF, 4);

        assertThat(signer.isConfirmationValid(foreign.token(), FeatureCode.BILLING, BillingMode.FULL, BillingMode.OFF, 4)).isFalse();
    }

    @Test
    void extendingTheExpiryOfATokenInvalidatesIt() {
        String token = signer.issueConfirmation(FeatureCode.BILLING, BillingMode.FULL, BillingMode.OFF, 4).token();
        String signature = token.substring(token.indexOf('.') + 1);
        long extendedExpiry = ISSUED_AT.plus(Duration.ofDays(1)).getEpochSecond();

        assertThat(signer.isConfirmationValid(extendedExpiry + "." + signature, FeatureCode.BILLING, BillingMode.FULL, BillingMode.OFF, 4)).isFalse();
    }

    @Test
    void malformedTokensAreRejected() {
        for (String malformed : new String[] {null, "", "no-separator", ".onlysignature", "abc.def", "123.", "9999999999.xxxx"}) {
            assertThat(signer.isConfirmationValid(malformed, FeatureCode.BILLING, BillingMode.FULL, BillingMode.OFF, 4)).as("%s", malformed).isFalse();
        }
    }

    private static FeatureTransitionConfirmationSigner signerAt(Instant now, String secret) {
        return new FeatureTransitionConfirmationSigner(secret, Clock.fixed(now, ZoneOffset.UTC));
    }
}
