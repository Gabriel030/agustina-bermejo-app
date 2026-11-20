package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.entity.RegularStudentCertificate;
import com.red_escuela.backend.graduation.certificate.regular.exception.RegularCertificateNotFoundException;
import com.red_escuela.backend.graduation.certificate.regular.exception.RegularCertificateNotRevocableException;
import com.red_escuela.backend.graduation.certificate.regular.exception.RevocationReasonInvalidException;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularStudentCertificateRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class RegularCertificateRevoker {

    private final RegularStudentCertificateRepository repository;
    private final CurrentActorFinder currentActorFinder;
    private final Clock clock;

    public RegularCertificateRevoker(RegularStudentCertificateRepository repository, CurrentActorFinder currentActorFinder, Clock clock) {
        this.repository = repository;
        this.currentActorFinder = currentActorFinder;
        this.clock = clock;
    }

    @Transactional
    public void revoke(Long certificateId, String rawReason) {
        RegularStudentCertificate certificate = repository.findById(certificateId)
                .orElseThrow(() -> new RegularCertificateNotFoundException());
        if (!certificate.isRevocable()) {
            throw new RegularCertificateNotRevocableException();
        }
        String reason = RegularCertificateReason.required(rawReason, () -> new RevocationReasonInvalidException());
        certificate.revoke(reason, currentActorFinder.currentPersonId(), LocalDateTime.now(clock));
        repository.save(certificate);
    }
}
