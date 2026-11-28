package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.EndedSectionFinder;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.testsupport.TermFixtures;
import com.red_escuela.backend.evaluation.api.ClosureNotificationTargetsFinder;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardAudit;
import com.red_escuela.backend.evaluation.entity.ExamBoardAuditAction;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.events.ExamBoardAutoClosedEvent;
import com.red_escuela.backend.evaluation.repository.CommissionClosureReminderRepository;
import com.red_escuela.backend.evaluation.repository.CommissionClosureRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardAuditRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardInstructorRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRegistrationRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRepository;
import com.red_escuela.backend.evaluation.repository.SectionGradeItemPublicationRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExamBoardReopenAndAutoCloseCycleTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-12-30T09:00:00Z"));
    private final Clock clock = new Clock() {
        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };

    private final ExamBoardRepository examBoardRepository = mock(ExamBoardRepository.class);
    private final ExamBoardAuditRepository auditRepository = mock(ExamBoardAuditRepository.class);
    private final ExamBoardInstructorRepository instructorRepository = mock(ExamBoardInstructorRepository.class);
    private final ExamBoardRegistrationRepository registrationRepository = mock(ExamBoardRegistrationRepository.class);
    private final ExamBoardManager examBoardManager = mock(ExamBoardManager.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final CurrentActorFinder actorFinder = mock(CurrentActorFinder.class);
    private final List<ExamBoardAudit> audits = new ArrayList<>();
    private final ExamBoard board = new ExamBoard();

    private final TermEndClosureReview review = new TermEndClosureReview(mock(EndedSectionFinder.class), mock(CommissionClosureRepository.class),
            mock(SectionGradeItemPublicationRepository.class), mock(ClosureNotificationTargetsFinder.class), examBoardRepository,
            registrationRepository, auditRepository, instructorRepository);
    private final TermEndEscalationJob job = new TermEndEscalationJob(review, mock(CommissionClosureReminderRepository.class),
            examBoardManager, registrationRepository, examBoardRepository, instructorRepository, eventPublisher, TransactionOperations.withoutTransaction(), clock);
    private final ExamBoardReopener reopener = new ExamBoardReopener(examBoardRepository, auditRepository, actorFinder, clock);

    private void wire() {
        Subject subject = new Subject();
        subject.setName("Análisis");
        board.setId(5L);
        board.setSubject(subject);
        board.setTerm(TermFixtures.newTerm());
        board.getTerm().setId(3L);
        board.setExamDate(LocalDateTime.of(2026, 12, 1, 10, 0));
        board.setStatus(ExamBoardStatus.CLOSED);
        when(examBoardRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(board));
        when(examBoardRepository.save(any(ExamBoard.class))).thenAnswer(call -> call.getArgument(0));
        when(examBoardRepository.findByStatusAndExamDateLessThanOrderByExamDate(any(), any()))
                .thenAnswer(call -> board.getStatus() == ExamBoardStatus.SCHEDULED ? List.of(board) : List.of());
        when(auditRepository.save(any(ExamBoardAudit.class))).thenAnswer(call -> {
            audits.add(call.getArgument(0));
            return call.getArgument(0);
        });
        when(auditRepository.findLastOccurredAt(5L, ExamBoardAuditAction.REOPENED))
                .thenAnswer(call -> audits.stream().map(ExamBoardAudit::getOccurredAt).max(LocalDateTime::compareTo));
        when(actorFinder.currentPersonId()).thenReturn(77L);
        when(instructorRepository.existsByExamBoardId(5L)).thenReturn(true);
        when(examBoardManager.close(5L)).thenAnswer(call -> {
            board.setStatus(ExamBoardStatus.CLOSED);
            return board;
        });
        when(registrationRepository.countByExamBoardIdAndStatus(5L, ExamBoardRegistrationStatus.ABSENT)).thenReturn(0L);
    }

    @Test
    void aReopenedBoardIsNotClosedAgainDuringTheGraceWindowCountedFromTheReopening() {
        wire();
        reopener.reopen(5L, "Error de carga en el acta");

        assertThat(job.closeExpiredExamBoards()).isZero();
        verify(examBoardManager, never()).close(any());

        now.set(Instant.parse("2027-01-13T09:00:00Z"));
        assertThat(job.closeExpiredExamBoards()).isZero();
        verify(examBoardManager, never()).close(any());
        assertThat(board.getStatus()).isEqualTo(ExamBoardStatus.SCHEDULED);
    }

    @Test
    void aReopenedBoardIsClosedOnceTheGraceWindowFromTheReopeningHasPassed() {
        wire();
        reopener.reopen(5L, "Error de carga en el acta");

        now.set(Instant.parse("2027-01-14T09:00:00Z"));

        assertThat(job.closeExpiredExamBoards()).isEqualTo(1);
        verify(examBoardManager).close(5L);
        verify(eventPublisher).publishEvent(any(ExamBoardAutoClosedEvent.class));
    }

    @Test
    void aBoardThatWasNeverReopenedKeepsTheGraceWindowFromItsExamDate() {
        wire();
        board.setStatus(ExamBoardStatus.SCHEDULED);
        now.set(Instant.parse("2026-12-16T09:00:00Z"));

        assertThat(review.graceReference(board)).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(job.closeExpiredExamBoards()).isEqualTo(1);
    }
}
