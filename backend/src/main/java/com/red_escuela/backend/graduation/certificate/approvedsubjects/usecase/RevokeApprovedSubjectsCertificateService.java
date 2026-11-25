package com.red_escuela.backend.graduation.certificate.approvedsubjects.usecase;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificateResponse;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.entity.ApprovedSubjectsCertificate;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.exception.ApprovedSubjectsCertificateNotAvailableException;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.exception.ApprovedSubjectsCertificateNotFoundException;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsActor;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsAuditLog;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsCertificateResponses;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsCertificateStore;
import com.red_escuela.backend.shared.UseCase;

import java.time.Clock;
import java.time.LocalDateTime;

@UseCase
public class RevokeApprovedSubjectsCertificateService implements RevokeApprovedSubjectsCertificateUseCase {

    private final ApprovedSubjectsCertificateStore store;
    private final ApprovedSubjectsCertificateResponses responses;
    private final ApprovedSubjectsActor actor;
    private final ApprovedSubjectsAuditLog auditLog;
    private final Clock clock;

    public RevokeApprovedSubjectsCertificateService(ApprovedSubjectsCertificateStore store,
                                                    ApprovedSubjectsCertificateResponses responses, ApprovedSubjectsActor actor,
                                                    ApprovedSubjectsAuditLog auditLog, Clock clock) {
        this.store = store;
        this.responses = responses;
        this.actor = actor;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Override
    public ApprovedSubjectsCertificateResponse revoke(Long id, String rawReason) {
        ApprovedSubjectsCertificate certificate = store.findById(id).orElseThrow(ApprovedSubjectsCertificateNotFoundException::new);
        if (!certificate.isRevocable()) {
            throw new ApprovedSubjectsCertificateNotAvailableException();
        }
        String reason = rawReason.strip();
        certificate.revoke(reason, actor.currentPersonId(), LocalDateTime.now(clock));
        ApprovedSubjectsCertificate saved = store.save(certificate);
        auditLog.certificateRevoked(id, reason);
        return responses.of(saved);
    }
}
