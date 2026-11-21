package com.red_escuela.backend.academicrecord.service;

import com.red_escuela.backend.academicrecord.repository.CalendarFeedTokenRepository;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class RevokeCalendarFeedTokenTransaction {

    private final CalendarFeedTokenRepository repository;
    private final AuditTrail auditTrail;
    private final Clock clock;

    public RevokeCalendarFeedTokenTransaction(CalendarFeedTokenRepository repository, AuditTrail auditTrail, Clock clock) {
        this.repository = repository;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    @Transactional
    public void revoke(Long studentId) {
        repository.findByStudentIdAndRevokedAtIsNull(studentId).ifPresent(active -> {
            active.setRevokedAt(LocalDateTime.now(clock));
            repository.save(active);
            auditTrail.record(new AuditRecord(AuditAction.CALENDAR_FEED_TOKEN_REVOKED, AuditEntityType.STUDENT, studentId,
                    null, null, null));
        });
    }
}
