package com.red_escuela.backend.graduation.certificate.verification;

import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.verification.dto.PublicCertificateDocumentKind;
import com.red_escuela.backend.graduation.certificate.verification.dto.PublicCertificateStatus;
import com.red_escuela.backend.graduation.certificate.verification.dto.PublicCertificateView;
import com.red_escuela.backend.graduation.certificate.verification.exception.CertificateNotFoundException;
import com.red_escuela.backend.graduation.certificate.verification.service.CertificateVerificationSource;
import com.red_escuela.backend.graduation.certificate.verification.service.PublicCertificateLookup;
import com.red_escuela.backend.graduation.certificate.verification.service.PublicVerificationRateLimiter;
import com.red_escuela.backend.shared.exception.RequestRateLimitExceededException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicCertificateLookupTest {

    private static final String KNOWN = "7QK3M9XH2VBN4T8R5W6YZ0A1B2";
    private static final String CLIENT = "203.0.113.7";

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final List<String> queried = new ArrayList<>();

    private final CertificateVerificationSource source = (code, now) -> {
        queried.add(code.value());
        return KNOWN.equals(code.value()) ? Optional.of(viewOf(PublicCertificateStatus.VALID)) : Optional.empty();
    };

    private PublicCertificateView viewOf(PublicCertificateStatus status) {
        return new PublicCertificateView(PublicCertificateDocumentKind.GRADUATION, status, "PEREZ, ANA", "*****678",
                "la carrera", "Tecnicatura", null, LocalDateTime.of(2026, 11, 1, 10, 0), null, null, null, 1800);
    }

    private PublicCertificateLookup lookupWith(int capacity, int missCost) {
        PublicVerificationRateLimiter limiter = new PublicVerificationRateLimiter(clock, capacity, 1, missCost, 100);
        return new PublicCertificateLookup(List.of(source), limiter, meters, clock);
    }

    @Test
    void knownCodeReturnsTheView() {
        PublicCertificateView view = lookupWith(20, 3).verify(KNOWN, CLIENT);

        assertThat(view.status()).isEqualTo(PublicCertificateStatus.VALID);
        assertThat(meters.counter("certificate.verification", "result", "valid").count()).isEqualTo(1.0);
    }

    @Test
    void lowercaseHyphenatedCodeIsAccepted() {
        PublicCertificateView view = lookupWith(20, 3).verify("7qk3m-9xh2v-bn4t8-r5w6y-z0a1b2", CLIENT);

        assertThat(view.holderFullName()).isEqualTo("PEREZ, ANA");
    }

    @Test
    void unknownMalformedAndWrongLengthCodesFailWithTheSameException() {
        PublicCertificateLookup lookup = lookupWith(100, 1);
        String unknown = CertificateCode.generate(new java.security.SecureRandom()).value();
        List<String> failures = new ArrayList<>();

        for (String code : new String[] {unknown, "not a code", "7QK3M9XH2VBN4T8R", "1284", "x".repeat(500), "7QK3M9XH2VBN4T8R5W6YZ0A1BU"}) {
            assertThatThrownBy(() -> lookup.verify(code, CLIENT)).isInstanceOf(CertificateNotFoundException.class)
                    .satisfies(exception -> failures.add(exception.getClass().getName() + exception.getMessage()));
        }

        assertThat(failures).hasSize(6).containsOnly(failures.get(0));
    }

    @Test
    void malformedCodeStillHitsEverySourceSoTimingDoesNotRevealFormatValidity() {
        PublicCertificateLookup lookup = lookupWith(100, 1);

        assertThatThrownBy(() -> lookup.verify("not a code", CLIENT)).isInstanceOf(CertificateNotFoundException.class);

        assertThat(queried).hasSize(1);
    }

    @Test
    void malformedCodeNeverMatchesEvenIfTheDummyLookupWouldFindSomething() {
        CertificateVerificationSource greedy = (code, now) -> Optional.of(viewOf(PublicCertificateStatus.VALID));
        PublicVerificationRateLimiter limiter = new PublicVerificationRateLimiter(clock, 100, 1, 1, 100);
        PublicCertificateLookup lookup = new PublicCertificateLookup(List.of(greedy), limiter, meters, clock);

        assertThatThrownBy(() -> lookup.verify("1284", CLIENT)).isInstanceOf(CertificateNotFoundException.class);
    }

    @Test
    void sequentialRegistryNumberDoesNotResolve() {
        PublicCertificateLookup lookup = lookupWith(100, 1);

        for (String number : new String[] {"1", "1284", "N.º 1.284", "0000000001"}) {
            assertThatThrownBy(() -> lookup.verify(number, CLIENT)).isInstanceOf(CertificateNotFoundException.class);
        }
    }

    @Test
    void exhaustedClientGetsRateLimitedWithRetryAfter() {
        PublicCertificateLookup lookup = lookupWith(2, 1);
        lookup.verify(KNOWN, CLIENT);
        lookup.verify(KNOWN, CLIENT);

        assertThatThrownBy(() -> lookup.verify(KNOWN, CLIENT)).isInstanceOfSatisfying(RequestRateLimitExceededException.class,
                exception -> assertThat(exception.retryAfter()).isPositive());
        assertThat(meters.counter("certificate.verification", "result", "limited").count()).isEqualTo(1.0);
    }

    @Test
    void missesCostMoreTokensThanHits() {
        PublicCertificateLookup lookup = lookupWith(6, 3);

        assertThatThrownBy(() -> lookup.verify("1284", CLIENT)).isInstanceOf(CertificateNotFoundException.class);
        assertThatThrownBy(() -> lookup.verify("1284", CLIENT)).isInstanceOf(CertificateNotFoundException.class);

        assertThatThrownBy(() -> lookup.verify(KNOWN, CLIENT)).isInstanceOf(RequestRateLimitExceededException.class);
    }

    @Test
    void differentClientsHaveIndependentBudgets() {
        PublicCertificateLookup lookup = lookupWith(1, 1);
        lookup.verify(KNOWN, CLIENT);

        assertThatThrownBy(() -> lookup.verify(KNOWN, CLIENT)).isInstanceOf(RequestRateLimitExceededException.class);
        assertThat(lookup.verify(KNOWN, "198.51.100.9").status()).isEqualTo(PublicCertificateStatus.VALID);
    }

    @Test
    void rateLimitedRequestDoesNotTouchTheSources() {
        PublicCertificateLookup lookup = lookupWith(1, 1);
        lookup.verify(KNOWN, CLIENT);
        queried.clear();

        assertThatThrownBy(() -> lookup.verify(KNOWN, CLIENT)).isInstanceOf(RequestRateLimitExceededException.class);

        assertThat(queried).isEmpty();
    }
}
