package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.api.SectionTermEndFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.domain.ClosureSectionKind;
import com.red_escuela.backend.evaluation.domain.ClosureSnapshotResult;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.entity.CommissionClosure;
import com.red_escuela.backend.evaluation.exception.PublishedGradesIncompleteException;
import com.red_escuela.backend.evaluation.repository.CommissionClosureAuditRepository;
import com.red_escuela.backend.evaluation.repository.CommissionClosureExceptionRepository;
import com.red_escuela.backend.evaluation.repository.CommissionClosureRepository;
import com.red_escuela.backend.evaluation.repository.CommissionClosureStudentRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommissionClosureApproverTest {

    private final CommissionClosureRepository closureRepository = mock(CommissionClosureRepository.class);
    private final CommissionClosureStudentRepository studentRepository = mock(CommissionClosureStudentRepository.class);
    private final CommissionClosureExceptionRepository exceptionRepository = mock(CommissionClosureExceptionRepository.class);
    private final ClosureSectionEnrollmentLocker locker = mock(ClosureSectionEnrollmentLocker.class);
    private final CommissionClosureSnapshotRecorder snapshotRecorder = mock(CommissionClosureSnapshotRecorder.class);
    private final ClosureColumnsFinder columnsFinder = mock(ClosureColumnsFinder.class);
    private final ClosureOutcomeApplier outcomeApplier = mock(ClosureOutcomeApplier.class);
    private final CurrentActorFinder actorFinder = mock(CurrentActorFinder.class);
    private final CommissionClosureApprover approver = new CommissionClosureApprover(
            mock(SectionFinder.class), mock(SectionTermEndFinder.class), closureRepository,
            mock(CommissionClosureAuditRepository.class), studentRepository, exceptionRepository, locker, snapshotRecorder,
            columnsFinder, outcomeApplier, mock(ClosureReportNumberAllocator.class), actorFinder,
            mock(ApplicationEventPublisher.class), Clock.fixed(Instant.parse("2026-12-20T10:00:00Z"), ZoneOffset.UTC));

    private CommissionClosure closureIn(ClosureStatus status) {
        CommissionClosure closure = CommissionClosure.builder().id(9L).status(status).build();
        when(closureRepository.findBySectionKindAndSectionId(SectionKind.PROGRAM, 5L)).thenReturn(Optional.of(closure));
        when(closureRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(closure));
        when(columnsFinder.allPublished(any())).thenReturn(true);
        when(actorFinder.currentPersonId()).thenReturn(1L);
        return closure;
    }

    @Test
    void enrollmentsAreLockedBeforeThePaymentStandingIsReadByTheSnapshotAndBeforeAnyResultIsApplied() {
        closureIn(ClosureStatus.REOPENED);
        when(snapshotRecorder.recordSnapshot(9L)).thenReturn(new ClosureSnapshotResult(List.of(), List.of()));
        when(studentRepository.findByClosureId(9L)).thenReturn(List.of());
        when(studentRepository.saveAll(any())).thenReturn(List.of());

        approver.approveClosure(ClosureSectionKind.PROGRAM, 5L, "L", "F", true);

        InOrder order = inOrder(locker, snapshotRecorder, outcomeApplier);
        order.verify(locker).lockMembersInAscendingOrder(SectionKind.PROGRAM, 5L);
        order.verify(snapshotRecorder).recordSnapshot(9L);
        order.verify(outcomeApplier).applyClosedResults(any(), any());
    }

    @Test
    void pendingStudentsAbortTheClosureBeforeAnythingIsApplied() {
        closureIn(ClosureStatus.DRAFT_AVAILABLE);
        when(snapshotRecorder.recordSnapshot(9L)).thenReturn(new ClosureSnapshotResult(List.of(), List.of(3L)));

        assertThatThrownBy(() -> approver.approveClosure(ClosureSectionKind.PROGRAM, 5L, "L", "F", true))
                .isInstanceOf(PublishedGradesIncompleteException.class);

        verify(outcomeApplier, never()).applyClosedResults(any(), any());
    }

    @Test
    void aTeacherConfirmedClosureKeepsItsSignedSnapshotAndStillLocksEnrollmentsBeforeApplyingResults() {
        closureIn(ClosureStatus.TEACHER_CONFIRMED);
        when(studentRepository.findByClosureId(9L)).thenReturn(List.of());
        when(studentRepository.saveAll(any())).thenReturn(List.of());

        approver.approveClosure(ClosureSectionKind.PROGRAM, 5L, "L", "F", false);

        verify(snapshotRecorder, never()).recordSnapshot(any());
        InOrder order = inOrder(locker, outcomeApplier);
        order.verify(locker).lockMembersInAscendingOrder(SectionKind.PROGRAM, 5L);
        order.verify(outcomeApplier).applyClosedResults(any(), any());
    }

    @Test
    void anUnconfirmedClosureIsRejectedUnlessTheCallerAsksToCloseWithoutConfirmation() {
        closureIn(ClosureStatus.DRAFT_AVAILABLE);

        assertThatThrownBy(() -> approver.approveClosure(ClosureSectionKind.PROGRAM, 5L, "L", "F", false))
                .isInstanceOf(com.red_escuela.backend.evaluation.exception.ClosureNotConfirmedException.class);
        verify(locker, never()).lockMembersInAscendingOrder(any(), any());
    }
}
