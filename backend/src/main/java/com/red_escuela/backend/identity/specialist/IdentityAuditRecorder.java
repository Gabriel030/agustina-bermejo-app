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

    public void recordStaffCreated(Person person, String initialRole) {
        auditTrail.record(new AuditRecord(AuditAction.STAFF_CREATED, AuditEntityType.PERSON,
                person.getId(), null, initialRole, null));
    }

    public void recordPermissionGridChanged(Long permissionId, String role, boolean granted, String reason) {
        auditTrail.record(new AuditRecord(AuditAction.PERMISSION_GRID_CHANGED, AuditEntityType.PERMISSION,
                permissionId, role + ":" + (granted ? "DENIED" : "GRANTED"), role + ":" + (granted ? "GRANTED" : "DENIED"),
                reason));
    }

    public void recordPermissionSensitiveChanged(Long permissionId, String role, boolean granted, String reason) {
        auditTrail.record(new AuditRecord(AuditAction.PERMISSION_SENSITIVE_CHANGED, AuditEntityType.PERMISSION,
                permissionId, role + ":" + (granted ? "DENIED" : "GRANTED"), role + ":" + (granted ? "GRANTED" : "DENIED"),
                reason));
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
