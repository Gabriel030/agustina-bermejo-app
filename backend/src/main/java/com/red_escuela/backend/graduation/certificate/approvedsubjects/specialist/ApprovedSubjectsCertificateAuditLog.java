package com.red_escuela.backend.graduation.certificate.approvedsubjects.specialist;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsAuditLog;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ApprovedSubjectsCertificateAuditLog implements ApprovedSubjectsAuditLog {

    private final AuditTrail auditTrail;

    public ApprovedSubjectsCertificateAuditLog(AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void certificateRevoked(Long certificateId, String reason) {
        auditTrail.record(new AuditRecord(AuditAction.APPROVED_SUBJECTS_CERT_REVOKED, AuditEntityType.CERTIFICATE_DOCUMENT,
                certificateId, "VALID", "REVOKED", reason));
    }
}
