package com.red_escuela.backend.graduation.certificate.approvedsubjects.specialist;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.entity.ApprovedSubjectsCertificate;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.entity.ApprovedSubjectsCertificateStatus;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsAuditLog;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.repository.ApprovedSubjectsCertificateRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class ApprovedSubjectsEnrollmentCertificateRevoker {

    private final ApprovedSubjectsCertificateRepository repository;
    private final ApprovedSubjectsAuditLog auditLog;
    private final Clock clock;

    public ApprovedSubjectsEnrollmentCertificateRevoker(ApprovedSubjectsCertificateRepository repository,
                                                        ApprovedSubjectsAuditLog auditLog, Clock clock) {
        this.repository = repository;
        this.auditLog = auditLog;
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
        }
    }
}
