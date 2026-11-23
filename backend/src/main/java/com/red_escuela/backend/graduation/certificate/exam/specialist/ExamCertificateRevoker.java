package com.red_escuela.backend.graduation.certificate.exam.specialist;

import com.red_escuela.backend.graduation.certificate.exam.dto.ExamCertificateResponse;
import com.red_escuela.backend.graduation.certificate.exam.entity.ExamCertificate;
import com.red_escuela.backend.graduation.certificate.exam.exception.ExamCertificateNotFoundException;
import com.red_escuela.backend.graduation.certificate.exam.exception.ExamCertificateNotRevocableException;
import com.red_escuela.backend.graduation.certificate.exam.repository.ExamCertificateRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamCertificateRevoker {

    private final ExamCertificateRepository repository;
    private final ExamCertificateResponses responses;
    private final CurrentActorFinder currentActorFinder;
    private final AuditTrail auditTrail;
    private final Clock clock;

    public ExamCertificateRevoker(ExamCertificateRepository repository, ExamCertificateResponses responses,
                                  CurrentActorFinder currentActorFinder, AuditTrail auditTrail, Clock clock) {
        this.repository = repository;
        this.responses = responses;
        this.currentActorFinder = currentActorFinder;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    @Transactional
    public ExamCertificateResponse revoke(Long id, String rawReason) {
        ExamCertificate certificate = repository.findById(id).orElseThrow(ExamCertificateNotFoundException::new);
        if (!certificate.isRevocable()) {
            throw new ExamCertificateNotRevocableException();
        }
        String reason = rawReason.strip();
        certificate.revoke(reason, currentActorFinder.currentPersonId(), LocalDateTime.now(clock));
        ExamCertificate saved = repository.saveAndFlush(certificate);
        auditTrail.record(new AuditRecord(AuditAction.EXAM_CERTIFICATE_REVOKED, AuditEntityType.EXAM_CERTIFICATE, saved.getId(),
                "VALID", "REVOKED", reason));
        return responses.of(saved);
    }
}
