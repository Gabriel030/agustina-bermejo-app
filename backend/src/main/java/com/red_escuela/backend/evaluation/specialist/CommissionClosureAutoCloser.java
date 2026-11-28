package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.api.SectionTermEndFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.api.ClosureReportNumberAllocator;
import com.red_escuela.backend.evaluation.entity.ClosureAuditAction;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.entity.CommissionClosure;
import com.red_escuela.backend.evaluation.entity.CommissionClosureAudit;
import com.red_escuela.backend.evaluation.entity.CommissionClosureException;
import com.red_escuela.backend.evaluation.entity.CommissionClosureStudent;
import com.red_escuela.backend.evaluation.events.ClosureApprovedEvent;
import com.red_escuela.backend.evaluation.repository.CommissionClosureAuditRepository;
import com.red_escuela.backend.evaluation.repository.CommissionClosureExceptionRepository;
import com.red_escuela.backend.evaluation.repository.CommissionClosureRepository;
import com.red_escuela.backend.evaluation.repository.CommissionClosureStudentRepository;
import com.red_escuela.backend.shared.domain.TermEndGracePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class CommissionClosureAutoCloser {

    private static final Logger log = LoggerFactory.getLogger(CommissionClosureAutoCloser.class);

    private final SectionFinder sectionFinder;
    private final SectionTermEndFinder sectionTermEndFinder;
    private final CommissionClosureRepository closureRepository;
    private final CommissionClosureAuditRepository auditRepository;
    private final CommissionClosureStudentRepository closureStudentRepository;
    private final CommissionClosureExceptionRepository exceptionRepository;
    private final ClosureSectionEnrollmentLocker enrollmentLocker;
    private final CommissionClosureSnapshotRecorder snapshotRecorder;
    private final ClosureOutcomeApplier outcomeApplier;
    private final ClosureReportNumberAllocator reportNumberAllocator;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public CommissionClosureAutoCloser(SectionFinder sectionFinder, SectionTermEndFinder sectionTermEndFinder,
            CommissionClosureRepository closureRepository, CommissionClosureAuditRepository auditRepository,
            CommissionClosureStudentRepository closureStudentRepository,
            CommissionClosureExceptionRepository exceptionRepository, ClosureSectionEnrollmentLocker enrollmentLocker,
            CommissionClosureSnapshotRecorder snapshotRecorder, ClosureOutcomeApplier outcomeApplier,
            ClosureReportNumberAllocator reportNumberAllocator, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.sectionFinder = sectionFinder;
        this.sectionTermEndFinder = sectionTermEndFinder;
        this.closureRepository = closureRepository;
        this.auditRepository = auditRepository;
        this.closureStudentRepository = closureStudentRepository;
        this.exceptionRepository = exceptionRepository;
        this.enrollmentLocker = enrollmentLocker;
        this.snapshotRecorder = snapshotRecorder;
        this.outcomeApplier = outcomeApplier;
        this.reportNumberAllocator = reportNumberAllocator;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public boolean closeIfGradesLoaded(SectionKind kind, Long sectionId) {
        sectionFinder.requireExists(kind, sectionId);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate termEnd = sectionTermEndFinder.termEndDate(kind, sectionId);
        if (termEnd == null || !TermEndGracePolicy.isGraceOver(termEnd, now.toLocalDate())) {
            return false;
        }
        closureRepository.insertDraftIfAbsent(kind, sectionId);
        Long closureId = closureRepository.findBySectionKindAndSectionId(kind, sectionId).orElseThrow().getId();
        CommissionClosure closure = closureRepository.findByIdForUpdate(closureId).orElseThrow();
        if (closure.getStatus() == ClosureStatus.CLOSED) {
            return false;
        }
        enrollmentLocker.lockMembersInAscendingOrder(kind, sectionId);
        boolean withoutConfirmation = closure.getStatus() != ClosureStatus.TEACHER_CONFIRMED;
        if (withoutConfirmation) {
            int pending = snapshotRecorder.recordSnapshot(closureId).pendingEnrollmentIds().size();
            if (pending > 0) {
                log.warn("El cierre automático deja matrículas sin nota: kind={}, sectionId={}, pendientes={}", kind, sectionId, pending);
            }
        }
        List<CommissionClosureStudent> rows = settleConditionalPromotions(closureId);
        if (rows.stream().noneMatch(row -> row.getFinalCondition() != null)) {
            return false;
        }
        outcomeApplier.applyClosedResults(kind, rows);
        closure.closeAutomatically(now, closure.getReportNumber() == null ? reportNumberAllocator.allocateNextReportNumber() : null);
        closureRepository.save(closure);
        auditRepository.save(CommissionClosureAudit.builder()
                .closureId(closureId)
                .action(ClosureAuditAction.CLOSED_AUTOMATICALLY)
                .reportVersion(closure.getReportVersion())
                .occurredAt(now)
                .build());
        eventPublisher.publishEvent(ClosureApprovedEvent.of(closureId, kind.name(), sectionId, closure.getReportNumber(),
                closure.getReportVersion(), withoutConfirmation));
        return true;
    }

    private List<CommissionClosureStudent> settleConditionalPromotions(Long closureId) {
        Set<Long> exempted = exceptionRepository.findByClosureIdAndRevokedAtIsNull(closureId).stream()
                .map(CommissionClosureException::getEnrollmentId).collect(Collectors.toSet());
        List<CommissionClosureStudent> rows = closureStudentRepository.findByClosureId(closureId);
        rows.forEach(row -> row.settleConditionalPromotion(exempted.contains(row.getEnrollmentId())));
        return closureStudentRepository.saveAll(rows);
    }
}
