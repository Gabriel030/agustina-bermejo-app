package com.red_escuela.backend.graduation.certificate.approvedsubjects.specialist;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.entity.ApprovedSubjectsCertificate;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.entity.ApprovedSubjectsCertificateStatus;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsAuditLog;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.repository.ApprovedSubjectsCertificateRepository;
import com.red_escuela.backend.graduation.events.CertificateInvalidatedEvent;
import com.red_escuela.backend.identity.api.StudentFinder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class ApprovedSubjectsEnrollmentCertificateRevoker {

    private final ApprovedSubjectsCertificateRepository repository;
    private final ApprovedSubjectsAuditLog auditLog;
    private final StudentFinder studentFinder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ApprovedSubjectsEnrollmentCertificateRevoker(ApprovedSubjectsCertificateRepository repository,
                                                        ApprovedSubjectsAuditLog auditLog, StudentFinder studentFinder,
                                                        ApplicationEventPublisher eventPublisher, Clock clock) {
        this.repository = repository;
        this.auditLog = auditLog;
        this.studentFinder = studentFinder;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public void revokeValidCertificates(Long programEnrollmentId, String reason) {
        LocalDateTime now = LocalDateTime.now(clock);
        for (ApprovedSubjectsCertificate certificate
                : repository.findByProgramEnrollmentIdAndStatus(programEnrollmentId, ApprovedSubjectsCertificateStatus.VALID)) {
            certificate.revoke(reason, null, now);
            repository.save(certificate);
            auditLog.certificateRevoked(certificate.getId(), reason);
            eventPublisher.publishEvent(CertificateInvalidatedEvent.of(certificate.getId(),
                    CertificateInvalidatedEvent.APPROVED_SUBJECTS,
                    studentFinder.requireById(certificate.getStudentId()).getPerson().getId(), reason));
        }
    }
}
