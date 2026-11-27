package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.testsupport.TermFixtures;
import com.red_escuela.backend.evaluation.dto.ExamBoardResponse;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardAudit;
import com.red_escuela.backend.evaluation.entity.ExamBoardAuditAction;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.exception.ExamBoardNotClosedException;
import com.red_escuela.backend.evaluation.exception.ExamBoardNotFoundException;
import com.red_escuela.backend.evaluation.exception.InvalidExamBoardReopenReasonException;
import com.red_escuela.backend.evaluation.repository.ExamBoardAuditRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExamBoardReopenerTest {

    private final ExamBoardRepository examBoardRepository = mock(ExamBoardRepository.class);
    private final ExamBoardAuditRepository auditRepository = mock(ExamBoardAuditRepository.class);
    private final CurrentActorFinder actorFinder = mock(CurrentActorFinder.class);
    private final ExamBoardReopener reopener = new ExamBoardReopener(examBoardRepository, auditRepository, actorFinder,
            Clock.fixed(Instant.parse("2026-12-20T10:00:00Z"), ZoneOffset.UTC));

    private ExamBoard board;

    @BeforeEach
    void setUp() {
        board = new ExamBoard();
        board.setId(5L);
        board.setTerm(TermFixtures.newTerm());
        board.getTerm().setId(3L);
        board.setExamDate(LocalDateTime.of(2026, 12, 10, 18, 0));
        board.setStatus(ExamBoardStatus.CLOSED);
        when(examBoardRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(board));
        when(examBoardRepository.save(any(ExamBoard.class))).thenAnswer(call -> call.getArgument(0));
        when(actorFinder.currentPersonId()).thenReturn(77L);
    }

    @Test
    void reopeningAClosedBoardSchedulesItAgainAndAuditsTheReason() {
        ExamBoardResponse response = reopener.reopen(5L, "  Error de carga en el acta  ");

        assertThat(response.status()).isEqualTo(ExamBoardStatus.SCHEDULED.name());
        ArgumentCaptor<ExamBoardAudit> audit = ArgumentCaptor.forClass(ExamBoardAudit.class);
        verify(auditRepository).save(audit.capture());
        assertThat(audit.getValue().getExamBoardId()).isEqualTo(5L);
        assertThat(audit.getValue().getAction()).isEqualTo(ExamBoardAuditAction.REOPENED);
        assertThat(audit.getValue().getActorPersonId()).isEqualTo(77L);
        assertThat(audit.getValue().getReason()).isEqualTo("Error de carga en el acta");
        assertThat(audit.getValue().getOccurredAt()).isEqualTo(LocalDateTime.of(2026, 12, 20, 10, 0));
    }

    @Test
    void aShortReasonIsRejectedWithoutTouchingTheBoard() {
        assertThatThrownBy(() -> reopener.reopen(5L, "corto"))
                .isInstanceOf(InvalidExamBoardReopenReasonException.class);
        assertThatThrownBy(() -> reopener.reopen(5L, null))
                .isInstanceOf(InvalidExamBoardReopenReasonException.class);

        assertThat(board.getStatus()).isEqualTo(ExamBoardStatus.CLOSED);
        verify(auditRepository, never()).save(any());
    }

    @Test
    void aBoardThatIsNotClosedCannotBeReopened() {
        board.setStatus(ExamBoardStatus.SCHEDULED);

        assertThatThrownBy(() -> reopener.reopen(5L, "Error de carga en el acta"))
                .isInstanceOf(ExamBoardNotClosedException.class);
        verify(auditRepository, never()).save(any());
    }

    @Test
    void aMissingBoardIsReportedAsNotFound() {
        assertThatThrownBy(() -> reopener.reopen(6L, "Error de carga en el acta"))
                .isInstanceOf(ExamBoardNotFoundException.class);
    }
}
