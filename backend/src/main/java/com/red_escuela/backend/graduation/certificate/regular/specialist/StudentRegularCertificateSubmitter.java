package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateSubmissionResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateChannel;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.shared.exception.RequestRateLimitExceededException;
import com.red_escuela.backend.shared.ratelimit.KeyedRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;

@Service
public class StudentRegularCertificateSubmitter {

    private static final Duration IDLE_WINDOW = Duration.ofMinutes(30);

    private final RegularEnrollmentFactsFinder factsFinder;
    private final RegularCertificateIssuer issuer;
    private final KeyedRateLimiter limiter;

    public StudentRegularCertificateSubmitter(RegularEnrollmentFactsFinder factsFinder, RegularCertificateIssuer issuer, Clock clock,
                                              @Value("${app.certificate.regular.request-rate-limit.capacity:5}") int capacity,
                                              @Value("${app.certificate.regular.request-rate-limit.refill-per-minute:1}") int refillPerMinute,
                                              @Value("${app.rate-limit.max-tracked-keys:50000}") int maxTrackedKeys) {
        this.factsFinder = factsFinder;
        this.issuer = issuer;
        this.limiter = new KeyedRateLimiter(capacity, refillPerMinute, maxTrackedKeys, IDLE_WINDOW, clock);
    }

    public RegularCertificateSubmissionResponse submit(RegularityKind kind, Long enrollmentId, Long studentId) {
        factsFinder.findOwnedBy(kind, enrollmentId, studentId);
        KeyedRateLimiter.Decision decision = limiter.tryConsume("student:" + studentId);
        if (!decision.allowed()) {
            throw new RequestRateLimitExceededException(decision.retryAfter());
        }
        return issuer.submitFromPortal(new IssueRegularCertificateCommand(kind, enrollmentId, false, null,
                RegularCertificateChannel.STUDENT_PORTAL));
    }
}
