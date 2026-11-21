package com.red_escuela.backend.graduation.certificate.specialist;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificate;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateNotFoundException;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateNotRevocableException;
import com.red_escuela.backend.graduation.certificate.repository.GraduationCertificateRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class GraduationCertificateRevoker {

    private final GraduationCertificateRepository repository;
    private final CurrentActorFinder currentActorFinder;
    private final Clock clock;

    public GraduationCertificateRevoker(GraduationCertificateRepository repository, CurrentActorFinder currentActorFinder, Clock clock) {
        this.repository = repository;
        this.currentActorFinder = currentActorFinder;
        this.clock = clock;
    }

    @Transactional
    public void revoke(Long certificateId, String reason) {
        revokeLocked(certificateId, reason);
    }

    GraduationCertificate revokeLocked(Long certificateId, String reason) {
        GraduationCertificate certificate = repository.findByIdForUpdate(certificateId)
                .orElseThrow(() -> new GraduationCertificateNotFoundException(certificateId));
        if (!certificate.isRevocable()) {
            throw new GraduationCertificateNotRevocableException();
        }
        certificate.revoke(reason.trim(), currentActorFinder.currentPersonId(), LocalDateTime.now(clock));
        return repository.saveAndFlush(certificate);
    }
}
