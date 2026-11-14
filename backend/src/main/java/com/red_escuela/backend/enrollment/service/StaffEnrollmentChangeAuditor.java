package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

@Service
public class StaffEnrollmentChangeAuditor {

    private final AuditTrail auditTrail;

    public StaffEnrollmentChangeAuditor(AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
    }

    @Transactional
    public void withdrawAndRecord(AuditEntityType entityType, Long enrollmentId, String reason, Runnable withdrawal) {
        withdrawal.run();
        auditTrail.record(new AuditRecord(AuditAction.ENROLLMENT_WITHDRAWN_BY_STAFF, entityType, enrollmentId,
                null, null, reason));
    }

    @Transactional
    public <T> T changeSectionAndRecord(Long sectionEnrollmentId, String reason, Supplier<T> change) {
        T result = change.get();
        auditTrail.record(new AuditRecord(AuditAction.ENROLLMENT_SECTION_CHANGED_BY_STAFF,
                AuditEntityType.SECTION_ENROLLMENT, sectionEnrollmentId, null, null, reason));
        return result;
    }
}
