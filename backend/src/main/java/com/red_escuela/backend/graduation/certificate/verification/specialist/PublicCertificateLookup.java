package com.red_escuela.backend.graduation.certificate.verification.specialist;

import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.exception.InvalidCertificateCodeException;
import com.red_escuela.backend.graduation.certificate.verification.dto.PublicCertificateView;
import com.red_escuela.backend.graduation.certificate.verification.exception.CertificateNotFoundException;
import com.red_escuela.backend.shared.exception.RequestRateLimitExceededException;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class PublicCertificateLookup {

    private static final Logger log = LoggerFactory.getLogger(PublicCertificateLookup.class);
    private static final int MAX_RAW_LENGTH = 64;
    private static final CertificateCode NEVER_ISSUED_CODE = new CertificateCode("0".repeat(CertificateCode.LENGTH));
    private static final String METRIC = "certificate.verification";

    private final List<CertificateVerificationSource> sources;
    private final PublicVerificationRateLimiter rateLimiter;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public PublicCertificateLookup(List<CertificateVerificationSource> sources, PublicVerificationRateLimiter rateLimiter,
                                   MeterRegistry meterRegistry, Clock clock) {
        this.sources = sources;
        this.rateLimiter = rateLimiter;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    public PublicCertificateView verify(String rawCode, String clientKey) {
        try {
            rateLimiter.acquire(clientKey);
        } catch (RequestRateLimitExceededException exception) {
            count("limited");
            log.warn("public_verification_rate_limited client={}", clientPrefix(clientKey));
            throw exception;
        }
        Optional<CertificateCode> parsed = parse(rawCode);
        CertificateCode lookupCode = parsed.orElse(NEVER_ISSUED_CODE);
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<PublicCertificateView> found = findInSources(lookupCode, now);
        if (parsed.isEmpty() || found.isEmpty()) {
            rateLimiter.chargeMiss(clientKey);
            count("not_found");
            throw new CertificateNotFoundException();
        }
        PublicCertificateView view = found.get();
        count(view.status().name().toLowerCase());
        return view;
    }

    private Optional<PublicCertificateView> findInSources(CertificateCode code, LocalDateTime now) {
        Optional<PublicCertificateView> result = Optional.empty();
        for (CertificateVerificationSource source : sources) {
            Optional<PublicCertificateView> candidate = source.findByCode(code, now);
            if (result.isEmpty()) {
                result = candidate;
            }
        }
        return result;
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
