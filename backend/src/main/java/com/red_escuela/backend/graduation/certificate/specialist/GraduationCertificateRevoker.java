package com.red_escuela.backend.graduation.certificate.specialist;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificate;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateNotFoundException;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateNotRevocableException;
import com.red_escuela.backend.graduation.certificate.repository.GraduationCertificateRepository;
import com.red_escuela.backend.graduation.certificate.regular.exception.RevocationReasonInvalidException;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class GraduationCertificateRevoker {

    private static final int MIN_REASON_LENGTH = 10;
    private static final int MAX_REASON_LENGTH = 500;

    private final GraduationCertificateRepository repository;
    private final CurrentActorFinder currentActorFinder;
    private final Clock clock;
    private final GraduationCertificateEventPublisher eventPublisher;

    public GraduationCertificateRevoker(GraduationCertificateRepository repository, CurrentActorFinder currentActorFinder, Clock clock,
                                      GraduationCertificateEventPublisher eventPublisher) {
        this.repository = repository;
        this.currentActorFinder = currentActorFinder;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void revoke(Long certificateId, String reason) {
        eventPublisher.revoked(revokeLocked(certificateId, reason));
    }

    GraduationCertificate revokeLocked(Long certificateId, String rawReason) {
        String reason = rawReason == null ? "" : rawReason.trim();
        if (reason.length() < MIN_REASON_LENGTH || reason.length() > MAX_REASON_LENGTH) {
            throw new RevocationReasonInvalidException();
        }
        GraduationCertificate certificate = repository.findByIdForUpdate(certificateId)
                .orElseThrow(() -> new GraduationCertificateNotFoundException(certificateId));
        if (!certificate.isRevocable()) {
            throw new GraduationCertificateNotRevocableException();
        }
        certificate.revoke(reason, currentActorFinder.currentPersonId(), LocalDateTime.now(clock));
        return repository.saveAndFlush(certificate);
    }
}
