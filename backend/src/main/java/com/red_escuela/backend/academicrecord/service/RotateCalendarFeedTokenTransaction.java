package com.red_escuela.backend.academicrecord.service;

import com.red_escuela.backend.academicrecord.dto.CalendarFeedTokenResponse;
import com.red_escuela.backend.academicrecord.entity.CalendarFeedToken;
import com.red_escuela.backend.academicrecord.repository.CalendarFeedTokenRepository;
import com.red_escuela.backend.academicrecord.specialist.CalendarFeedLocator;
import com.red_escuela.backend.academicrecord.specialist.CalendarFeedTokenFactory;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class RotateCalendarFeedTokenTransaction {

    static final int LOCK_NAMESPACE = 0x0CA1FEED;

    private final CalendarFeedTokenRepository repository;
    private final CalendarFeedTokenFactory tokenFactory;
    private final CalendarFeedLocator locator;
    private final AuditTrail auditTrail;
    private final Clock clock;

    public RotateCalendarFeedTokenTransaction(CalendarFeedTokenRepository repository, CalendarFeedTokenFactory tokenFactory,
                                              CalendarFeedLocator locator, AuditTrail auditTrail, Clock clock) {
        this.repository = repository;
        this.tokenFactory = tokenFactory;
        this.locator = locator;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    @Transactional
    public CalendarFeedTokenResponse rotate(Long studentId) {
        repository.lockStudentFeedTokens(LOCK_NAMESPACE, studentId);
        LocalDateTime now = LocalDateTime.now(clock);
        repository.findByStudentIdAndRevokedAtIsNull(studentId).ifPresent(previous -> {
            previous.setRevokedAt(now);
            repository.saveAndFlush(previous);
        });
        CalendarFeedTokenFactory.GeneratedCalendarFeedToken generated = tokenFactory.create();
        CalendarFeedToken saved = repository.save(CalendarFeedToken.builder()
                .studentId(studentId)
                .tokenHash(generated.tokenHash())
                .createdAt(now)
                .build());
        auditTrail.record(new AuditRecord(AuditAction.CALENDAR_FEED_TOKEN_ROTATED, AuditEntityType.STUDENT, studentId,
                null, null, null));
        return new CalendarFeedTokenResponse(locator.urlFor(generated.token()), generated.token(), saved.getCreatedAt());
    }
}
