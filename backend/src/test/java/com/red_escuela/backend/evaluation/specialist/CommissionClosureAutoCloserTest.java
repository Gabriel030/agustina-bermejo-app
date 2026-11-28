package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.api.SectionTermEndFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.api.ClosureReportNumberAllocator;
import com.red_escuela.backend.evaluation.domain.ClosureSnapshotResult;
import com.red_escuela.backend.evaluation.entity.ClosureAuditAction;
import com.red_escuela.backend.evaluation.entity.ClosureFinalCondition;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.entity.CommissionClosure;
import com.red_escuela.backend.evaluation.entity.CommissionClosureAudit;
import com.red_escuela.backend.evaluation.entity.CommissionClosureStudent;
import com.red_escuela.backend.evaluation.events.ClosureApprovedEvent;
import com.red_escuela.backend.evaluation.repository.CommissionClosureAuditRepository;
import com.red_escuela.backend.evaluation.repository.CommissionClosureExceptionRepository;
import com.red_escuela.backend.evaluation.repository.CommissionClosureRepository;
import com.red_escuela.backend.evaluation.repository.CommissionClosureStudentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommissionClosureAutoCloserTest {

    private static final LocalDate TERM_END = LocalDate.of(2026, 12, 10);

    private final SectionTermEndFinder termEndFinder = mock(SectionTermEndFinder.class);
    private final CommissionClosureRepository closureRepository = mock(CommissionClosureRepository.class);
    private final CommissionClosureAuditRepository auditRepository = mock(CommissionClosureAuditRepository.class);
    private final CommissionClosureStudentRepository studentRepository = mock(CommissionClosureStudentRepository.class);
    private final CommissionClosureExceptionRepository exceptionRepository = mock(CommissionClosureExceptionRepository.class);
    private final ClosureSectionEnrollmentLocker locker = mock(ClosureSectionEnrollmentLocker.class);
    private final CommissionClosureSnapshotRecorder snapshotRecorder = mock(CommissionClosureSnapshotRecorder.class);
    private final ClosureOutcomeApplier outcomeApplier = mock(ClosureOutcomeApplier.class);
    private final ClosureReportNumberAllocator numberAllocator = mock(ClosureReportNumberAllocator.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private CommissionClosureAutoCloser closerOn(LocalDate today) {
        when(termEndFinder.termEndDate(SectionKind.PROGRAM, 5L)).thenReturn(TERM_END);
        when(numberAllocator.allocateNextReportNumber()).thenReturn("ACTA-2026-0001");
        when(exceptionRepository.findByClosureIdAndRevokedAtIsNull(9L)).thenReturn(List.of());
        when(studentRepository.saveAll(any())).thenAnswer(call -> call.getArgument(0));
        return new CommissionClosureAutoCloser(mock(SectionFinder.class), termEndFinder, closureRepository, auditRepository,
                studentRepository, exceptionRepository, locker, snapshotRecorder, outcomeApplier, numberAllocator, eventPublisher,
                Clock.fixed(today.atTime(9, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    private CommissionClosure closureIn(ClosureStatus status) {
        CommissionClosure closure = CommissionClosure.builder().id(9L).sectionKind(SectionKind.PROGRAM).sectionId(5L)
                .status(status).reportVersion(1).build();
        when(closureRepository.findBySectionKindAndSectionId(SectionKind.PROGRAM, 5L)).thenReturn(Optional.of(closure));
        when(closureRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(closure));
        return closure;
    }

    private CommissionClosureStudent graded(Long enrollmentId, ClosureFinalCondition condition) {
        return CommissionClosureStudent.builder().closureId(9L).enrollmentId(enrollmentId).finalCondition(condition).build();
    }

    @Test
    void aGradedSectionPastTheGraceWindowIsClosedWithoutActorBookOrFolioAndAudited() {
        CommissionClosure closure = closureIn(ClosureStatus.DRAFT_AVAILABLE);
        CommissionClosureAutoCloser closer = closerOn(TERM_END.plusDays(15));
        when(snapshotRecorder.recordSnapshot(9L)).thenReturn(new ClosureSnapshotResult(List.of(), List.of()));
        when(studentRepository.findByClosureId(9L)).thenReturn(List.of(graded(1L, ClosureFinalCondition.FAILED)));

        assertThat(closer.closeIfGradesLoaded(SectionKind.PROGRAM, 5L)).isTrue();

        assertThat(closure.getStatus()).isEqualTo(ClosureStatus.CLOSED);
        assertThat(closure.isClosedAutomatically()).isTrue();
        assertThat(closure.getClosedById()).isNull();
        assertThat(closure.getBook()).isNull();
        assertThat(closure.getFolio()).isNull();
        assertThat(closure.getReportNumber()).isEqualTo("ACTA-2026-0001");
        ArgumentCaptor<CommissionClosureAudit> audit = ArgumentCaptor.forClass(CommissionClosureAudit.class);
        verify(auditRepository).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo(ClosureAuditAction.CLOSED_AUTOMATICALLY);
        assertThat(audit.getValue().getActorPersonId()).isNull();
        verify(eventPublisher).publishEvent(any(ClosureApprovedEvent.class));
    }

    @Test
    void enrollmentsAreLockedAndTheSnapshotRecordedBeforeAnyResultIsApplied() {
        closureIn(ClosureStatus.DRAFT_AVAILABLE);
        CommissionClosureAutoCloser closer = closerOn(TERM_END.plusDays(15));
        when(snapshotRecorder.recordSnapshot(9L)).thenReturn(new ClosureSnapshotResult(List.of(), List.of()));
        when(studentRepository.findByClosureId(9L)).thenReturn(List.of(graded(1L, ClosureFinalCondition.PROMOTES)));

        closer.closeIfGradesLoaded(SectionKind.PROGRAM, 5L);

        InOrder order = inOrder(closureRepository, locker, snapshotRecorder, outcomeApplier);
        order.verify(closureRepository).insertDraftIfAbsent(SectionKind.PROGRAM, 5L);
        order.verify(locker).lockMembersInAscendingOrder(SectionKind.PROGRAM, 5L);
        order.verify(snapshotRecorder).recordSnapshot(9L);
        order.verify(outcomeApplier).applyClosedResults(any(), any());
    }

    @Test
    void aClosureAlreadyClosedByAPersonIsNeverOverwritten() {
        CommissionClosure closure = CommissionClosure.builder().id(9L).status(ClosureStatus.CLOSED).closedById(3L).build();
        when(closureRepository.findBySectionKindAndSectionId(SectionKind.PROGRAM, 5L)).thenReturn(Optional.of(closure));
        when(closureRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(closure));
        CommissionClosureAutoCloser closer = closerOn(TERM_END.plusDays(15));

        assertThat(closer.closeIfGradesLoaded(SectionKind.PROGRAM, 5L)).isFalse();

        assertThat(closure.getClosedById()).isEqualTo(3L);
        assertThat(closure.isClosedAutomatically()).isFalse();
        verify(outcomeApplier, never()).applyClosedResults(any(), any());
        verify(auditRepository, never()).save(any());
    }

    @Test
    void aSectionInsideTheGraceWindowIsNotTouched() {
        CommissionClosureAutoCloser closer = closerOn(TERM_END.plusDays(14));

        assertThat(closer.closeIfGradesLoaded(SectionKind.PROGRAM, 5L)).isFalse();

        verify(closureRepository, never()).insertDraftIfAbsent(any(), any());
        verify(outcomeApplier, never()).applyClosedResults(any(), any());
    }

    @Test
    void aSectionWithoutAnyGradedEnrollmentIsNotClosed() {
        CommissionClosure closure = closureIn(ClosureStatus.DRAFT_AVAILABLE);
        CommissionClosureAutoCloser closer = closerOn(TERM_END.plusDays(15));
        when(snapshotRecorder.recordSnapshot(9L)).thenReturn(new ClosureSnapshotResult(List.of(), List.of(1L, 2L)));
        when(studentRepository.findByClosureId(9L)).thenReturn(List.of());

        assertThat(closer.closeIfGradesLoaded(SectionKind.PROGRAM, 5L)).isFalse();

        assertThat(closure.getStatus()).isEqualTo(ClosureStatus.DRAFT_AVAILABLE);
        verify(outcomeApplier, never()).applyClosedResults(any(), any());
        verify(auditRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(ClosureApprovedEvent.class));
    }

    @Test
    void aTeacherConfirmedClosureKeepsItsSignedSnapshot() {
        CommissionClosure closure = closureIn(ClosureStatus.TEACHER_CONFIRMED);
        CommissionClosureAutoCloser closer = closerOn(TERM_END.plusDays(15));
        when(studentRepository.findByClosureId(9L)).thenReturn(List.of(graded(1L, ClosureFinalCondition.PROMOTES)));

        assertThat(closer.closeIfGradesLoaded(SectionKind.PROGRAM, 5L)).isTrue();

        verify(snapshotRecorder, never()).recordSnapshot(any());
        assertThat(closure.isClosedWithoutTeacherConfirmation()).isFalse();
    }

    @Test
    void aConditionalPromotionWithoutExceptionGoesToFinal() {
        closureIn(ClosureStatus.DRAFT_AVAILABLE);
        CommissionClosureAutoCloser closer = closerOn(TERM_END.plusDays(15));
        when(snapshotRecorder.recordSnapshot(9L)).thenReturn(new ClosureSnapshotResult(List.of(), List.of()));
        CommissionClosureStudent conditional = graded(1L, ClosureFinalCondition.PROMOTION_CONDITIONAL);
        when(studentRepository.findByClosureId(9L)).thenReturn(List.of(conditional));

        closer.closeIfGradesLoaded(SectionKind.PROGRAM, 5L);

        assertThat(conditional.getFinalCondition()).isEqualTo(ClosureFinalCondition.GOES_TO_FINAL);
    }

    @Test
    void aSectionWithSomeLiveEnrollmentWithoutAGradeIsNotClosedAndNothingIsApplied() {
        CommissionClosure closure = closureIn(ClosureStatus.DRAFT_AVAILABLE);
        CommissionClosureAutoCloser closer = closerOn(TERM_END.plusDays(15));
        when(snapshotRecorder.recordSnapshot(9L)).thenReturn(new ClosureSnapshotResult(List.of(), List.of(2L)));
        when(studentRepository.findByClosureId(9L)).thenReturn(List.of(graded(1L, ClosureFinalCondition.PROMOTES)));

        assertThat(closer.closeIfGradesLoaded(SectionKind.PROGRAM, 5L)).isFalse();

        assertThat(closure.getStatus()).isEqualTo(ClosureStatus.DRAFT_AVAILABLE);
        verify(outcomeApplier, never()).applyClosedResults(any(), any());
        verify(auditRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(ClosureApprovedEvent.class));
    }

    @Test
    void runningTwiceClosesOnceAndTheSecondRunChangesNothing() {
        closureIn(ClosureStatus.DRAFT_AVAILABLE);
        CommissionClosureAutoCloser closer = closerOn(TERM_END.plusDays(15));
        when(snapshotRecorder.recordSnapshot(9L)).thenReturn(new ClosureSnapshotResult(List.of(), List.of()));
        when(studentRepository.findByClosureId(9L)).thenReturn(List.of(graded(1L, ClosureFinalCondition.PROMOTES)));

        assertThat(closer.closeIfGradesLoaded(SectionKind.PROGRAM, 5L)).isTrue();
        assertThat(closer.closeIfGradesLoaded(SectionKind.PROGRAM, 5L)).isFalse();

        verify(outcomeApplier, times(1)).applyClosedResults(any(), any());
        verify(auditRepository, times(1)).save(any());
    }
}
