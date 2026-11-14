package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.dto.WithdrawStudentRequest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import com.red_escuela.backend.shared.domain.StatusType;
import org.springframework.stereotype.Component;

@Component
public class IdentityAuditRecorder {

    private final AuditTrail auditTrail;

    public IdentityAuditRecorder(AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
    }

    public void recordPasswordResetCompleted(Person person) {
        auditTrail.record(new AuditRecord(AuditAction.PASSWORD_RESET_COMPLETED, AuditEntityType.PERSON,
                person.getId(), null, null, null));
    }

    public void recordPasswordChanged(Person person) {
        auditTrail.record(new AuditRecord(AuditAction.PASSWORD_CHANGED, AuditEntityType.PERSON,
                person.getId(), null, null, null));
    }

    public void recordStudentWithdrawn(Long studentId, WithdrawStudentRequest request) {
        auditTrail.record(new AuditRecord(AuditAction.STUDENT_WITHDRAWN, AuditEntityType.STUDENT, studentId,
                null, StatusType.DELETED.name(), request.reason().name()));
    }
}
