package com.red_escuela.backend.graduation.certificate.verification.specialist;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.exception.InvalidCertificateCodeException;
import com.red_escuela.backend.graduation.certificate.specialist.VerifiableDocumentRegistry;
import com.red_escuela.backend.graduation.certificate.verification.dto.PublicDocumentVerificationResponse;
import com.red_escuela.backend.graduation.certificate.verification.exception.CertificateNotFoundException;
import com.red_escuela.backend.shared.exception.RequestRateLimitExceededException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PublicDocumentLookup {

    private static final Logger log = LoggerFactory.getLogger(PublicDocumentLookup.class);
    private static final int MAX_RAW_LENGTH = 64;
    private static final CertificateCode NEVER_ISSUED_CODE = new CertificateCode("0".repeat(CertificateCode.LENGTH));
    private static final String METRIC = "certificate.verification";

    private final Map<CertificateDocumentType, DocumentVerificationSource> sources = new EnumMap<>(CertificateDocumentType.class);
    private final VerifiableDocumentRegistry registry;
    private final PublicVerificationRateLimiter rateLimiter;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public PublicDocumentLookup(List<DocumentVerificationSource> sources, VerifiableDocumentRegistry registry,
                                PublicVerificationRateLimiter rateLimiter, MeterRegistry meterRegistry, Clock clock) {
        sources.forEach(source -> {
            if (this.sources.putIfAbsent(source.type(), source) != null) {
                throw new IllegalStateException("Más de una fuente de verificación para el tipo " + source.type());
            }
        });
        this.registry = registry;
        this.rateLimiter = rateLimiter;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    public PublicDocumentVerificationResponse verify(String rawCode, String clientKey) {
        try {
            rateLimiter.acquire(clientKey);
        } catch (RequestRateLimitExceededException exception) {
            count("limited");
            log.warn("public_verification_rate_limited client={}", clientPrefix(clientKey));
            throw exception;
        }
        Optional<CertificateCode> parsed = parse(rawCode);
        Optional<PublicDocumentVerificationResponse> found = resolve(parsed.orElse(NEVER_ISSUED_CODE))
                .filter(response -> parsed.isPresent());
        if (found.isEmpty()) {
            rateLimiter.chargeMiss(clientKey);
            count("not_found");
            throw new CertificateNotFoundException();
        }
        count(found.get().status().name().toLowerCase());
        return found.get();
    }

    private Optional<PublicDocumentVerificationResponse> resolve(CertificateCode code) {
        LocalDateTime now = LocalDateTime.now(clock);
        return registry.find(code).flatMap(ref -> Optional.ofNullable(sources.get(ref.type()))
                .flatMap(source -> source.find(ref.sourceId(), now)));
    }

    private Optional<CertificateCode> parse(String rawCode) {
        if (rawCode == null || rawCode.length() > MAX_RAW_LENGTH) {
            return Optional.empty();
        }
        try {
            return Optional.of(CertificateCode.parse(rawCode));
        } catch (InvalidCertificateCodeException exception) {
            return Optional.empty();
        }
    }

    private void count(String result) {
        meterRegistry.counter(METRIC, "result", result).increment();
    }

    private static String clientPrefix(String clientKey) {
        if (clientKey == null) {
            return "unknown";
        }
        int cut = Math.max(clientKey.lastIndexOf('.'), clientKey.lastIndexOf(':'));
        return cut > 0 ? clientKey.substring(0, cut) : "unknown";
    }
}
