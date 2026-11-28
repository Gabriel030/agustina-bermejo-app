package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.evaluation.dto.ExamBoardActaFile;
import com.red_escuela.backend.evaluation.dto.ExamBoardRegistrationSummaryResponse;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.exception.ExamBoardCanceledException;
import com.red_escuela.backend.evaluation.exception.NotExamBoardTribunalMemberException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExamBoardActaExporterTest {

    private final ExamBoardManager manager = mock(ExamBoardManager.class);
    private final ExamBoardActaPdfWriter writer = mock(ExamBoardActaPdfWriter.class);
    private final ExamBoardActaExporter exporter = new ExamBoardActaExporter(manager, writer);

    private ExamBoard board(ExamBoardStatus status) {
        ExamBoard board = new ExamBoard();
        board.setId(5L);
        board.setTerm(new Term());
        board.setSubject(new Subject());
        board.setExamDate(LocalDateTime.of(2026, 12, 10, 18, 30));
        board.setStatus(status);
        return board;
    }

    private ExamBoardRegistrationSummaryResponse registrant(String name, String status) {
        return new ExamBoardRegistrationSummaryResponse(1L, name, "30111222", "Carrera", 1, status, null, 1L, null, null, null, null, null, null);
    }

    @Test
    void canceledBoardIsAConflictAndNeverWritesAPdf() {
        when(manager.get(5L)).thenReturn(board(ExamBoardStatus.CANCELED));

        assertThatThrownBy(() -> exporter.export(5L, null)).isInstanceOf(ExamBoardCanceledException.class);

        verify(writer, never()).write(any(), any());
    }

    @Test
    void teacherOutsideTheTribunalNeverWritesAPdf() {
        org.mockito.Mockito.doThrow(new NotExamBoardTribunalMemberException(9L, 5L)).when(manager).requireTribunalMember(5L, 9L);

        assertThatThrownBy(() -> exporter.export(5L, 9L)).isInstanceOf(NotExamBoardTribunalMemberException.class);

        verify(writer, never()).write(any(), any());
    }

    @Test
    void withdrawnRegistrationsAreLeftOutOfTheActa() {
        when(manager.get(5L)).thenReturn(board(ExamBoardStatus.SCHEDULED));
        when(manager.listRegistrantsWithDetail(5L)).thenReturn(List.of(registrant("Activo", "REGISTERED"), registrant("Baja", "WITHDRAWN")));
        when(writer.write(any(), any())).thenReturn(new byte[] {1});

        ExamBoardActaFile file = exporter.export(5L, null);

        assertThat(file.fileName()).isEqualTo("acta-mesa-5.pdf");
        verify(writer).write(any(), org.mockito.ArgumentMatchers.argThat(list -> list.size() == 1 && list.get(0).studentName().equals("Activo")));
    }
}
