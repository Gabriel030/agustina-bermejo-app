package com.red_escuela.backend.graduation.certificate.verification;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.specialist.VerifiableDocumentRegistry;
import com.red_escuela.backend.graduation.certificate.specialist.VerifiableDocumentRegistry.VerifiableDocumentRef;
import com.red_escuela.backend.graduation.certificate.verification.dto.PublicDocumentVerificationResponse;
import com.red_escuela.backend.graduation.certificate.verification.exception.CertificateNotFoundException;
import com.red_escuela.backend.graduation.certificate.verification.specialist.DocumentVerificationSource;
import com.red_escuela.backend.graduation.certificate.verification.specialist.PublicDocumentLookup;
import com.red_escuela.backend.graduation.certificate.verification.specialist.PublicVerificationRateLimiter;
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

class PublicDocumentLookupRateLimitTest {

    private static final String KNOWN = "7QK3M9XH2VBN4T8R5W6YZ0A1B2";
    private static final String CLIENT = "203.0.113.7";

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC);
    private final List<String> queried = new ArrayList<>();

    private final VerifiableDocumentRegistry registry = new VerifiableDocumentRegistry(null, null) {
        @Override
        public Optional<VerifiableDocumentRef> find(com.red_escuela.backend.graduation.certificate.engine.CertificateCode code) {
            queried.add(code.value());
            return KNOWN.equals(code.value())
                    ? Optional.of(new VerifiableDocumentRef(CertificateDocumentType.GRADUATION_CERTIFICATE, 1L))
                    : Optional.empty();
        }
    };

    private final DocumentVerificationSource source = new DocumentVerificationSource() {
        @Override
        public CertificateDocumentType type() {
            return CertificateDocumentType.GRADUATION_CERTIFICATE;
        }

        @Override
        public Optional<PublicDocumentVerificationResponse> find(Long sourceId, LocalDateTime now) {
            queried.add("source");
            return Optional.of(new PublicDocumentVerificationResponse(CertificateDocumentType.GRADUATION_CERTIFICATE,
                    DocumentValidityStatus.VALID, "PEREZ, ANA", "*****678", LocalDateTime.of(2026, 11, 1, 10, 0), null, null, null, null));
        }
    };

    private PublicDocumentLookup lookupWith(int capacity, int missCost) {
        PublicVerificationRateLimiter limiter = new PublicVerificationRateLimiter(clock, capacity, 1, missCost, 100);
        return new PublicDocumentLookup(List.of(source), registry, limiter, new SimpleMeterRegistry(), clock);
    }

    @Test
    void exhaustedClientGetsRateLimitedWithRetryAfter() {
        PublicDocumentLookup lookup = lookupWith(2, 1);
        lookup.verify(KNOWN, CLIENT);
        lookup.verify(KNOWN, CLIENT);

        assertThatThrownBy(() -> lookup.verify(KNOWN, CLIENT)).isInstanceOfSatisfying(RequestRateLimitExceededException.class,
                exception -> assertThat(exception.retryAfter()).isPositive());
    }

    @Test
    void missesCostMoreTokensThanHits() {
        PublicDocumentLookup lookup = lookupWith(6, 3);

        assertThatThrownBy(() -> lookup.verify("1284", CLIENT)).isInstanceOf(CertificateNotFoundException.class);
        assertThatThrownBy(() -> lookup.verify("1284", CLIENT)).isInstanceOf(CertificateNotFoundException.class);

        assertThatThrownBy(() -> lookup.verify(KNOWN, CLIENT)).isInstanceOf(RequestRateLimitExceededException.class);
    }

    @Test
    void differentClientsHaveIndependentBudgets() {
        PublicDocumentLookup lookup = lookupWith(1, 1);
        lookup.verify(KNOWN, CLIENT);

        assertThatThrownBy(() -> lookup.verify(KNOWN, CLIENT)).isInstanceOf(RequestRateLimitExceededException.class);
        assertThat(lookup.verify(KNOWN, "198.51.100.9").status()).isEqualTo(DocumentValidityStatus.VALID);
    }

    @Test
    void rateLimitedRequestDoesNotTouchTheSources() {
        PublicDocumentLookup lookup = lookupWith(1, 1);
        lookup.verify(KNOWN, CLIENT);
        queried.clear();

        assertThatThrownBy(() -> lookup.verify(KNOWN, CLIENT)).isInstanceOf(RequestRateLimitExceededException.class);

        assertThat(queried).isEmpty();
    }

    @Test
    void ipv6AddressesOfTheSameSlash64ShareOneBucket() {
        PublicDocumentLookup lookup = lookupWith(1, 1);
        lookup.verify(KNOWN, "2001:db8:1:2:aaaa::1");

        assertThatThrownBy(() -> lookup.verify(KNOWN, "2001:db8:1:2:bbbb::9")).isInstanceOf(RequestRateLimitExceededException.class);
        assertThat(lookup.verify(KNOWN, "2001:db8:1:3::1").status()).isEqualTo(DocumentValidityStatus.VALID);
        assertThat(PublicVerificationRateLimiter.keyOf("203.0.113.7")).isEqualTo("203.0.113.7");
    }
}
