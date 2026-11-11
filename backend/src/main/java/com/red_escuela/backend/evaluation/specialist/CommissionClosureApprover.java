package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.api.SectionTermEndFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.domain.ClosureSectionKind;
import com.red_escuela.backend.evaluation.domain.ClosureSnapshotResult;
import com.red_escuela.backend.evaluation.entity.ClosureAuditAction;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.entity.CommissionClosure;
import com.red_escuela.backend.evaluation.entity.CommissionClosureAudit;
import com.red_escuela.backend.evaluation.entity.CommissionClosureException;
import com.red_escuela.backend.evaluation.entity.CommissionClosureStudent;
import com.red_escuela.backend.evaluation.events.ClosureApprovedEvent;
import com.red_escuela.backend.evaluation.exception.ClosureAlreadyClosedException;
import com.red_escuela.backend.evaluation.exception.ClosureBeforeTermEndException;
import com.red_escuela.backend.evaluation.exception.ClosureNotAvailableException;
import com.red_escuela.backend.evaluation.exception.ClosureNotConfirmedException;
import com.red_escuela.backend.evaluation.exception.PublishedGradesIncompleteException;
import com.red_escuela.backend.evaluation.repository.CommissionClosureAuditRepository;
import com.red_escuela.backend.evaluation.repository.CommissionClosureExceptionRepository;
import com.red_escuela.backend.evaluation.repository.CommissionClosureRepository;
import com.red_escuela.backend.evaluation.repository.CommissionClosureStudentRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CommissionClosureApprover {

    private final SectionFinder sectionFinder;
    private final SectionTermEndFinder sectionTermEndFinder;
    private final CommissionClosureRepository closureRepository;
    private final CommissionClosureAuditRepository auditRepository;
    private final CommissionClosureStudentRepository closureStudentRepository;
    private final CommissionClosureExceptionRepository exceptionRepository;
    private final ClosureSectionEnrollmentLocker enrollmentLocker;
    private final CommissionClosureSnapshotRecorder snapshotRecorder;
    private final ClosureColumnsFinder closureColumnsFinder;
    private final ClosureOutcomeApplier outcomeApplier;
    private final ClosureReportNumberAllocator reportNumberAllocator;
    private final CurrentActorFinder currentActorFinder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public CommissionClosureApprover(SectionFinder sectionFinder, SectionTermEndFinder sectionTermEndFinder,
            CommissionClosureRepository closureRepository, CommissionClosureAuditRepository auditRepository,
            CommissionClosureStudentRepository closureStudentRepository,
            CommissionClosureExceptionRepository exceptionRepository, ClosureSectionEnrollmentLocker enrollmentLocker,
            CommissionClosureSnapshotRecorder snapshotRecorder, ClosureColumnsFinder closureColumnsFinder,
            ClosureOutcomeApplier outcomeApplier, ClosureReportNumberAllocator reportNumberAllocator,
            CurrentActorFinder currentActorFinder, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.sectionFinder = sectionFinder;
        this.sectionTermEndFinder = sectionTermEndFinder;
        this.closureRepository = closureRepository;
        this.auditRepository = auditRepository;
        this.closureStudentRepository = closureStudentRepository;
        this.exceptionRepository = exceptionRepository;
        this.enrollmentLocker = enrollmentLocker;
        this.snapshotRecorder = snapshotRecorder;
        this.closureColumnsFinder = closureColumnsFinder;
        this.outcomeApplier = outcomeApplier;
        this.reportNumberAllocator = reportNumberAllocator;
        this.currentActorFinder = currentActorFinder;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public void approveClosure(ClosureSectionKind sectionKind, Long sectionId, String book, String folio,
            boolean closeWithoutTeacherConfirmation) {
        SectionKind kind = sectionKind.toSectionKind();
        sectionFinder.requireExists(kind, sectionId);
        Long closureId = closureRepository.findBySectionKindAndSectionId(kind, sectionId)
                .orElseThrow(() -> new ClosureNotAvailableException(sectionId)).getId();
        CommissionClosure closure = closureRepository.findByIdForUpdate(closureId).orElseThrow();
        boolean withoutConfirmation = closure.getStatus() != ClosureStatus.TEACHER_CONFIRMED;
        requireApprovable(closure, withoutConfirmation && !closeWithoutTeacherConfirmation);
        LocalDateTime now = LocalDateTime.now(clock);
        requireTermEnded(kind, sectionId, now.toLocalDate());
        if (!closureColumnsFinder.allPublished(closureColumnsFinder.findColumns(kind, sectionId))) {
            throw new PublishedGradesIncompleteException(sectionId);
        }
        enrollmentLocker.lockMembersInAscendingOrder(kind, sectionId);
        if (withoutConfirmation) {
            ClosureSnapshotResult snapshot = snapshotRecorder.recordSnapshot(closureId);
            if (!snapshot.pendingEnrollmentIds().isEmpty()) {
                throw new PublishedGradesIncompleteException(sectionId);
            }
        }
        List<CommissionClosureStudent> rows = settleConditionalPromotions(closureId);
        outcomeApplier.applyClosedResults(kind, rows);
        Long actorPersonId = currentActorFinder.currentPersonId();
        closure.close(actorPersonId, now, reportNumberAllocator.allocateNextReportNumber(), book.trim(), folio.trim(), withoutConfirmation);
        closureRepository.save(closure);
        auditRepository.save(CommissionClosureAudit.builder()
                .closureId(closureId)
                .action(withoutConfirmation ? ClosureAuditAction.CLOSED_WITHOUT_TEACHER_CONFIRMATION : ClosureAuditAction.CLOSED)
                .reportVersion(closure.getReportVersion())
                .actorPersonId(actorPersonId)
                .occurredAt(now)
                .build());
        eventPublisher.publishEvent(ClosureApprovedEvent.of(closureId, kind.name(), sectionId, closure.getReportNumber(),
                closure.getReportVersion(), withoutConfirmation));
    }

    private void requireApprovable(CommissionClosure closure, boolean missingTeacherConfirmation) {
        if (closure.getStatus() == ClosureStatus.CLOSED) {
            throw new ClosureAlreadyClosedException(closure.getId());
        }
        if (missingTeacherConfirmation) {
            throw new ClosureNotConfirmedException(closure.getId());
        }
    }

    private void requireTermEnded(SectionKind kind, Long sectionId, LocalDate today) {
        LocalDate termEnd = sectionTermEndFinder.termEndDate(kind, sectionId);
        if (termEnd != null && today.isBefore(termEnd)) {
            throw new ClosureBeforeTermEndException(sectionId);
        }
    }

    private List<CommissionClosureStudent> settleConditionalPromotions(Long closureId) {
        Set<Long> exempted = exceptionRepository.findByClosureIdAndRevokedAtIsNull(closureId).stream()
                .map(CommissionClosureException::getEnrollmentId).collect(Collectors.toSet());
        List<CommissionClosureStudent> rows = closureStudentRepository.findByClosureId(closureId);
        rows.forEach(row -> row.settleConditionalPromotion(exempted.contains(row.getEnrollmentId())));
        return closureStudentRepository.saveAll(rows);
    }
}
