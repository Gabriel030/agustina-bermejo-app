package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.evaluation.dto.PendingClosureState;
import com.red_escuela.backend.evaluation.dto.PendingSectionClosureResponse;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.events.ExamBoardAutoClosedEvent;
import com.red_escuela.backend.evaluation.events.TermEndClosureOverdueEvent;
import com.red_escuela.backend.evaluation.repository.CommissionClosureReminderRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRegistrationRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRepository;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TermEndEscalationJobTest {

    private static final LocalDate TERM_END = LocalDate.of(2026, 12, 10);

    private final TermEndClosureReview closureReview = mock(TermEndClosureReview.class);
    private final CommissionClosureReminderRepository reminderRepository = mock(CommissionClosureReminderRepository.class);
    private final ExamBoardManager examBoardManager = mock(ExamBoardManager.class);
    private final ExamBoardRegistrationRepository registrationRepository = mock(ExamBoardRegistrationRepository.class);
    private final ExamBoardRepository examBoardRepository = mock(ExamBoardRepository.class);
    private final java.util.Map<Long, ExamBoard> boards = new java.util.HashMap<>();
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private TermEndEscalationJob jobOn(LocalDate today) {
        Clock clock = Clock.fixed(today.atTime(9, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        when(reminderRepository.insertIfAbsent(any(), any(), anyInt(), any())).thenReturn(1);
        when(examBoardRepository.findByIdForUpdate(any())).thenAnswer(call -> Optional.ofNullable(boards.get((Long) call.getArgument(0))));
        when(closureReview.graceReference(any())).thenAnswer(call -> ((ExamBoard) call.getArgument(0)).getExamDate().toLocalDate());
        return new TermEndEscalationJob(closureReview, reminderRepository, examBoardManager, registrationRepository, examBoardRepository, eventPublisher,
                TransactionOperations.withoutTransaction(), clock);
    }

    private PendingSectionClosureResponse pending(Long sectionId, boolean graceOver) {
        return new PendingSectionClosureResponse("PROGRAM", sectionId, "Teorías · " + sectionId, TERM_END,
                graceOver ? 15 : 10, graceOver, PendingClosureState.NOT_STARTED, false);
    }

    private ExamBoard board(Long id, LocalDateTime examDate) {
        Subject subject = new Subject();
        subject.setName("Análisis");
        ExamBoard board = new ExamBoard();
        board.setId(id);
        board.setSubject(subject);
        board.setStatus(ExamBoardStatus.SCHEDULED);
        boards.put(id, board);
        board.setExamDate(examDate);
        return board;
    }

    @Test
    void aSectionStillInsideTheGraceWindowIsNotEscalated() {
        TermEndEscalationJob job = jobOn(TERM_END.plusDays(14));
        when(closureReview.pendingSections(any())).thenReturn(List.of(pending(9L, false)));

        assertThat(job.escalateOverdueSections()).isZero();
        verify(eventPublisher, never()).publishEvent(any(TermEndClosureOverdueEvent.class));
    }

    @Test
    void aSectionPastTheGraceWindowIsEscalatedToDirectionExactlyOnce() {
        TermEndEscalationJob job = jobOn(TERM_END.plusDays(15));
        when(closureReview.pendingSections(any())).thenReturn(List.of(pending(9L, true)));

        assertThat(job.escalateOverdueSections()).isEqualTo(1);

        ArgumentCaptor<TermEndClosureOverdueEvent> event = ArgumentCaptor.forClass(TermEndClosureOverdueEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().sectionId()).isEqualTo(9L);
        assertThat(event.getValue().sectionLabel()).isEqualTo("Teorías · 9");
        verify(reminderRepository).insertIfAbsent(eq(SectionKind.PROGRAM), eq(9L), eq(15), any());
    }

    @Test
    void anAlreadyEscalatedSectionIsNotPublishedAgain() {
        TermEndEscalationJob job = jobOn(TERM_END.plusDays(16));
        when(closureReview.pendingSections(any())).thenReturn(List.of(pending(9L, true)));
        when(reminderRepository.insertIfAbsent(any(), any(), anyInt(), any())).thenReturn(0);

        assertThat(job.escalateOverdueSections()).isZero();
        verify(eventPublisher, never()).publishEvent(any(TermEndClosureOverdueEvent.class));
    }

    @Test
    void aFailingSectionDoesNotStopTheEscalationOfTheOthers() {
        TermEndEscalationJob job = jobOn(TERM_END.plusDays(15));
        when(closureReview.pendingSections(any())).thenReturn(List.of(pending(8L, true), pending(9L, true)));
        when(reminderRepository.insertIfAbsent(eq(SectionKind.PROGRAM), eq(8L), anyInt(), any())).thenThrow(new IllegalStateException("boom"));
        when(reminderRepository.insertIfAbsent(eq(SectionKind.PROGRAM), eq(9L), anyInt(), any())).thenReturn(1);

        assertThat(job.escalateOverdueSections()).isEqualTo(1);
    }

    @Test
    void anExamBoardWithinTheGraceWindowStaysScheduled() {
        LocalDate today = LocalDate.of(2026, 12, 24);
        TermEndEscalationJob job = jobOn(today);
        when(closureReview.expiredScheduledBoards(any())).thenReturn(List.of(board(5L, today.minusDays(14).atTime(10, 0))));

        assertThat(job.closeExpiredExamBoards()).isZero();
        verify(examBoardManager, never()).close(any());
    }

    @Test
    void anExamBoardPastTheGraceWindowIsClosedAndDirectionIsToldHowManyStayedAbsent() {
        LocalDate today = LocalDate.of(2026, 12, 25);
        TermEndEscalationJob job = jobOn(today);
        ExamBoard board = board(5L, today.minusDays(15).atTime(10, 0));
        when(closureReview.expiredScheduledBoards(any())).thenReturn(List.of(board));
        when(examBoardManager.close(5L)).thenReturn(board);
        when(registrationRepository.countByExamBoardIdAndStatus(5L, ExamBoardRegistrationStatus.ABSENT)).thenReturn(0L, 3L);

        assertThat(job.closeExpiredExamBoards()).isEqualTo(1);

        ArgumentCaptor<ExamBoardAutoClosedEvent> event = ArgumentCaptor.forClass(ExamBoardAutoClosedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().absentCount()).isEqualTo(3L);
        assertThat(event.getValue().subjectName()).isEqualTo("Análisis");
    }

    @Test
    void aBoardThatFailsToCloseDoesNotStopTheOthers() {
        LocalDate today = LocalDate.of(2026, 12, 25);
        TermEndEscalationJob job = jobOn(today);
        ExamBoard failing = board(5L, today.minusDays(20).atTime(10, 0));
        ExamBoard healthy = board(6L, today.minusDays(20).atTime(10, 0));
        when(closureReview.expiredScheduledBoards(any())).thenReturn(List.of(failing, healthy));
        when(examBoardManager.close(5L)).thenThrow(new IllegalStateException("boom"));
        when(examBoardManager.close(6L)).thenReturn(healthy);

        assertThat(job.closeExpiredExamBoards()).isEqualTo(1);
    }

    @Test
    void aBoardAlreadyClosedByTheTimeTheJobGetsToItIsNotAnnouncedAgain() {
        LocalDate today = LocalDate.of(2026, 12, 25);
        TermEndEscalationJob job = jobOn(today);
        ExamBoard stale = board(5L, today.minusDays(20).atTime(10, 0));
        when(closureReview.expiredScheduledBoards(any())).thenReturn(List.of(stale));
        stale.setStatus(ExamBoardStatus.CLOSED);

        assertThat(job.closeExpiredExamBoards()).isZero();
        verify(examBoardManager, never()).close(any());
        verify(eventPublisher, never()).publishEvent(any(ExamBoardAutoClosedEvent.class));
    }

    @Test
    void theAbsentCountOnlyIncludesTheRegistrationsMarkedInThisRun() {
        LocalDate today = LocalDate.of(2026, 12, 25);
        TermEndEscalationJob job = jobOn(today);
        ExamBoard board = board(5L, today.minusDays(20).atTime(10, 0));
        when(closureReview.expiredScheduledBoards(any())).thenReturn(List.of(board));
        when(examBoardManager.close(5L)).thenReturn(board);
        when(registrationRepository.countByExamBoardIdAndStatus(5L, ExamBoardRegistrationStatus.ABSENT)).thenReturn(2L, 5L);

        job.closeExpiredExamBoards();

        ArgumentCaptor<ExamBoardAutoClosedEvent> event = ArgumentCaptor.forClass(ExamBoardAutoClosedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().absentCount()).isEqualTo(3L);
    }
}
