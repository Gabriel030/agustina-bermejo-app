package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.domain.ClosureSectionKind;
import com.red_escuela.backend.evaluation.entity.ClosureAuditAction;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.entity.CommissionClosure;
import com.red_escuela.backend.evaluation.entity.CommissionClosureAudit;
import com.red_escuela.backend.evaluation.entity.CommissionClosureException;
import com.red_escuela.backend.evaluation.events.ClosureExceptionGrantedEvent;
import com.red_escuela.backend.evaluation.exception.ClosureAlreadyClosedException;
import com.red_escuela.backend.evaluation.exception.ClosureExceptionAlreadyGrantedException;
import com.red_escuela.backend.evaluation.exception.ClosureExceptionNotFoundException;
import com.red_escuela.backend.evaluation.exception.ClosureNotAvailableException;
import com.red_escuela.backend.evaluation.exception.ClosureStudentNotFoundException;
import com.red_escuela.backend.evaluation.exception.InvalidClosureExceptionReasonException;
import com.red_escuela.backend.evaluation.repository.CommissionClosureAuditRepository;
import com.red_escuela.backend.evaluation.repository.CommissionClosureExceptionRepository;
import com.red_escuela.backend.evaluation.repository.CommissionClosureRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class ClosureExceptionManager {

    static final int MINIMUM_REASON_LENGTH = 10;

    private final SectionFinder sectionFinder;
    private final CommissionClosureRepository closureRepository;
    private final CommissionClosureAuditRepository auditRepository;
    private final CommissionClosureExceptionRepository exceptionRepository;
    private final ClosureSectionEnrollmentLocker enrollmentLocker;
    private final CurrentActorFinder currentActorFinder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ClosureExceptionManager(SectionFinder sectionFinder, CommissionClosureRepository closureRepository,
            CommissionClosureAuditRepository auditRepository, CommissionClosureExceptionRepository exceptionRepository,
            ClosureSectionEnrollmentLocker enrollmentLocker, CurrentActorFinder currentActorFinder,
            ApplicationEventPublisher eventPublisher, Clock clock) {
        this.sectionFinder = sectionFinder;
        this.closureRepository = closureRepository;
        this.auditRepository = auditRepository;
        this.exceptionRepository = exceptionRepository;
        this.enrollmentLocker = enrollmentLocker;
        this.currentActorFinder = currentActorFinder;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public void grantException(ClosureSectionKind sectionKind, Long sectionId, Long enrollmentId, String reason) {
        SectionKind kind = sectionKind.toSectionKind();
        String trimmedReason = reason == null ? "" : reason.trim();
        if (trimmedReason.length() < MINIMUM_REASON_LENGTH) {
            throw new InvalidClosureExceptionReasonException(MINIMUM_REASON_LENGTH);
        }
        CommissionClosure closure = lockOpenClosure(kind, sectionId);
        if (!enrollmentLocker.memberIds(kind, sectionId).contains(enrollmentId)) {
            throw new ClosureStudentNotFoundException(enrollmentId);
        }
        if (exceptionRepository.findByClosureIdAndEnrollmentIdAndRevokedAtIsNull(closure.getId(), enrollmentId).isPresent()) {
            throw new ClosureExceptionAlreadyGrantedException(enrollmentId);
        }
        Long actorPersonId = currentActorFinder.currentPersonId();
        LocalDateTime now = LocalDateTime.now(clock);
        exceptionRepository.save(CommissionClosureException.builder()
                .closureId(closure.getId())
                .enrollmentId(enrollmentId)
                .reason(trimmedReason)
                .grantedByPersonId(actorPersonId)
                .grantedAt(now)
                .build());
        recordAudit(closure, ClosureAuditAction.EXCEPTION_GRANTED, actorPersonId, trimmedReason, now);
        eventPublisher.publishEvent(ClosureExceptionGrantedEvent.of(closure.getId(), kind.name(), sectionId, enrollmentId, trimmedReason));
    }

    @Transactional
    public void revokeException(ClosureSectionKind sectionKind, Long sectionId, Long enrollmentId) {
        SectionKind kind = sectionKind.toSectionKind();
        CommissionClosure closure = lockOpenClosure(kind, sectionId);
        CommissionClosureException exception = exceptionRepository
                .findByClosureIdAndEnrollmentIdAndRevokedAtIsNull(closure.getId(), enrollmentId)
                .orElseThrow(() -> new ClosureExceptionNotFoundException(enrollmentId));
        Long actorPersonId = currentActorFinder.currentPersonId();
        LocalDateTime now = LocalDateTime.now(clock);
        exception.revoke(actorPersonId, now);
        exceptionRepository.save(exception);
        recordAudit(closure, ClosureAuditAction.EXCEPTION_REVOKED, actorPersonId, exception.getReason(), now);
    }

    private CommissionClosure lockOpenClosure(SectionKind kind, Long sectionId) {
        sectionFinder.requireExists(kind, sectionId);
        Long closureId = closureRepository.findBySectionKindAndSectionId(kind, sectionId)
                .orElseThrow(() -> new ClosureNotAvailableException(sectionId)).getId();
        CommissionClosure closure = closureRepository.findByIdForUpdate(closureId).orElseThrow();
        if (closure.getStatus() == ClosureStatus.CLOSED) {
            throw new ClosureAlreadyClosedException(closureId);
        }
        return closure;
    }

    private void recordAudit(CommissionClosure closure, ClosureAuditAction action, Long actorPersonId, String reason,
            LocalDateTime at) {
        auditRepository.save(CommissionClosureAudit.builder()
                .closureId(closure.getId())
                .action(action)
                .reportVersion(closure.getReportVersion())
                .actorPersonId(actorPersonId)
                .reason(reason)
                .occurredAt(at)
                .build());
    }
}
