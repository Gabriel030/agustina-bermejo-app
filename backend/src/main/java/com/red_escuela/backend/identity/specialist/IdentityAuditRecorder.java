package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.domain.PermissionModuleMode;
import com.red_escuela.backend.identity.dto.WithdrawStudentRequest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.PreceptorAssignment;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import com.red_escuela.backend.shared.domain.StatusType;
import org.springframework.stereotype.Component;

@Component
public class IdentityAuditRecorder {

    private static final int AUDIT_VALUE_MAX_LENGTH = 100;

    private final AuditTrail auditTrail;

    public IdentityAuditRecorder(AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
    }

    public void recordPasswordResetCompleted(Person person) {
        auditTrail.record(new AuditRecord(AuditAction.PASSWORD_RESET_COMPLETED, AuditEntityType.PERSON,
                person.getId(), null, null, null));
    }

    public void recordPreceptorAssigned(PreceptorAssignment assignment) {
        auditTrail.record(new AuditRecord(AuditAction.PRECEPTOR_ASSIGNED, AuditEntityType.PRECEPTOR_ASSIGNMENT,
                assignment.getId(), null, describe(assignment), null));
    }

    public void recordPreceptorUnassigned(PreceptorAssignment assignment) {
        auditTrail.record(new AuditRecord(AuditAction.PRECEPTOR_UNASSIGNED, AuditEntityType.PRECEPTOR_ASSIGNMENT,
                assignment.getId(), describe(assignment), null, null));
    }

    private String describe(PreceptorAssignment assignment) {
        String section = assignment.getProgramCohortId() != null
                ? "programCohortId=" + assignment.getProgramCohortId()
                : assignment.getSubjectSectionId() != null
                ? "subjectSectionId=" + assignment.getSubjectSectionId()
                : "courseSectionId=" + assignment.getCourseSectionId();
        return "personId=" + assignment.getPersonId() + ";" + section;
    }

    public void recordStaffCreated(Person person, String initialRole) {
        auditTrail.record(new AuditRecord(AuditAction.STAFF_CREATED, AuditEntityType.PERSON,
                person.getId(), null, initialRole, null));
    }

    public void recordPermissionGridChanged(Long permissionId, String role, boolean granted, String reason) {
        recordPermissionChange(AuditAction.PERMISSION_GRID_CHANGED, permissionId, role, granted, reason);
    }

    public void recordPermissionSensitiveChanged(Long permissionId, String permissionCode, String role, boolean granted,
                                                 String reason) {
        String subject = permissionCode + ":" + role;
        if (subject.length() + ":DENIED".length() > AUDIT_VALUE_MAX_LENGTH) {
            subject = permissionCode;
        }
        recordPermissionChange(AuditAction.PERMISSION_SENSITIVE_CHANGED, permissionId, subject, granted, reason);
    }

    private void recordPermissionChange(AuditAction action, Long permissionId, String subject, boolean granted,
                                        String reason) {
        auditTrail.record(new AuditRecord(action, AuditEntityType.PERMISSION, permissionId,
                subject + ":" + (granted ? "DENIED" : "GRANTED"), subject + ":" + (granted ? "GRANTED" : "DENIED"),
                reason));
    }

    public void recordPermissionModeChanged(Long modeSettingId, String role, String module,
                                            PermissionModuleMode previous, PermissionModuleMode current, String reason) {
        auditTrail.record(new AuditRecord(AuditAction.PERMISSION_GRID_CHANGED, AuditEntityType.PERMISSION,
                modeSettingId, "Cambio de modo " + role + "/" + module + ": " + previous.name(),
                "Cambio de modo " + role + "/" + module + ": " + current.name(), reason));
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
