package com.red_escuela.backend.graduation.debtexception.specialist;

import com.red_escuela.backend.graduation.debtexception.entity.DocumentDebtException;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DebtExceptionAuditRecorder {

    private final AuditTrail auditTrail;

    public DebtExceptionAuditRecorder(AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void issued(DocumentDebtException exception) {
        auditTrail.record(new AuditRecord(AuditAction.DOCUMENT_DEBT_EXC_ISSUED, AuditEntityType.DOCUMENT_DEBT_EXCEPTION,
                exception.getId(), null, "ACTIVE", describe(exception.getDocumentType().name(), exception.getStudentId())
                + ";expiresAt=" + exception.getExpiresAt()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void used(Long exceptionId, String documentType, Long studentId) {
        auditTrail.record(new AuditRecord(AuditAction.DOCUMENT_DEBT_EXC_USED, AuditEntityType.DOCUMENT_DEBT_EXCEPTION,
                exceptionId, "ACTIVE", "USED", describe(documentType, studentId)));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void expired(Long exceptionId) {
        auditTrail.record(new AuditRecord(AuditAction.DOCUMENT_DEBT_EXC_EXPIRED, AuditEntityType.DOCUMENT_DEBT_EXCEPTION,
                exceptionId, "ACTIVE", "EXPIRED", null));
    }

    private static String describe(String documentType, Long studentId) {
        return "documentType=" + documentType + ";studentId=" + studentId;
    }
}
