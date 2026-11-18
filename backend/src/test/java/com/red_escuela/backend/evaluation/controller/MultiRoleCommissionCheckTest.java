package com.red_escuela.backend.evaluation.controller;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.service.CurrentInstructorService;
import com.red_escuela.backend.evaluation.domain.ClosureViewerProfile;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.evaluation.dto.ExamBoardResponse;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.exception.NotExamBoardTribunalMemberException;
import com.red_escuela.backend.evaluation.specialist.ExamBoardManager;
import com.red_escuela.backend.evaluation.usecase.closure.GetClosureSheetCommand;
import com.red_escuela.backend.evaluation.usecase.closure.GetClosureSheetUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MultiRoleCommissionCheckTest {

    private final CurrentInstructorService currentInstructorService = mock(CurrentInstructorService.class);

    private Authentication authenticatedWith(String... roles) {
        return new UsernamePasswordAuthenticationToken("7", "n/a",
                Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
    }

    private void currentInstructorIs(long id) {
        Instructor instructor = mock(Instructor.class);
        when(instructor.getId()).thenReturn(id);
        when(currentInstructorService.resolveCurrentInstructor()).thenReturn(instructor);
    }

    private GetClosureSheetCommand readClosureSheetAs(String... roles) {
        GetClosureSheetUseCase getClosureSheet = mock(GetClosureSheetUseCase.class);
        CommissionClosureController controller = new CommissionClosureController(getClosureSheet, null, null, null,
                null, null, null, null, null, currentInstructorService);
        controller.getProgramSectionClosureSheet(10L, authenticatedWith(roles));
        ArgumentCaptor<GetClosureSheetCommand> captor = ArgumentCaptor.forClass(GetClosureSheetCommand.class);
        verify(getClosureSheet).get(captor.capture());
        return captor.getValue();
    }

    @Test
    void teacherWithStaffRoleKeepsStaffViewOnClosureSheet() {
        GetClosureSheetCommand command = readClosureSheetAs("ROLE_DOCENTE", "ROLE_DIRECTOR");

        assertThat(command.viewer()).isEqualTo(ClosureViewerProfile.STAFF);
        assertThat(command.instructorId()).isNull();
        verify(currentInstructorService, never()).resolveCurrentInstructor();
    }

    @Test
    void teacherOnlyMustBelongToTheCommissionOnClosureSheet() {
        currentInstructorIs(55L);

        GetClosureSheetCommand command = readClosureSheetAs("ROLE_DOCENTE");

        assertThat(command.viewer()).isEqualTo(ClosureViewerProfile.TEACHER);
        assertThat(command.instructorId()).isEqualTo(55L);
    }

    @Test
    void teacherWithStudentRoleMustBelongToTheCommissionOnClosureSheet() {
        currentInstructorIs(55L);

        GetClosureSheetCommand command = readClosureSheetAs("ROLE_DOCENTE", "ROLE_ALUMNO");

        assertThat(command.viewer()).isEqualTo(ClosureViewerProfile.TEACHER);
        assertThat(command.instructorId()).isEqualTo(55L);
    }

    @Test
    void staffWithoutTeacherRoleKeepsFullAccessOnClosureSheet() {
        GetClosureSheetCommand command = readClosureSheetAs("ROLE_PRECEPTOR");

        assertThat(command.viewer()).isEqualTo(ClosureViewerProfile.STAFF);
        assertThat(command.instructorId()).isNull();
        verify(currentInstructorService, never()).resolveCurrentInstructor();
    }

    private ExamBoardManager examBoardManagerReturningBoard(long id) {
        ExamBoard board = new ExamBoard();
        board.setId(id);
        board.setTerm(new Term());
        board.setStatus(ExamBoardStatus.SCHEDULED);
        ExamBoardManager examBoardManager = mock(ExamBoardManager.class);
        when(examBoardManager.get(id)).thenReturn(board);
        return examBoardManager;
    }

    private ExamBoardResponse readExamBoardAs(ExamBoardManager examBoardManager, String... roles) {
        ExamBoardController controller = new ExamBoardController(examBoardManager, currentInstructorService);
        return controller.get(3L, authenticatedWith(roles));
    }

    @Test
    void teacherWithDirectorRoleSkipsTribunalCheckOnExamBoard() {
        ExamBoardManager examBoardManager = examBoardManagerReturningBoard(3L);

        ExamBoardResponse response = readExamBoardAs(examBoardManager, "ROLE_DOCENTE", "ROLE_DIRECTOR");

        assertThat(response.id()).isEqualTo(3L);
        verify(examBoardManager, never()).requireTribunalMember(any(), any());
    }

    @Test
    void teacherWithAdministrativeRoleSkipsTribunalCheckOnExamBoard() {
        ExamBoardManager examBoardManager = examBoardManagerReturningBoard(3L);

        ExamBoardResponse response = readExamBoardAs(examBoardManager, "ROLE_DOCENTE", "ROLE_ADMINISTRATIVO");

        assertThat(response.id()).isEqualTo(3L);
        verify(examBoardManager, never()).requireTribunalMember(any(), any());
    }

    @Test
    void teacherWithStudentRoleMustBeTribunalMemberToReadExamBoard() {
        currentInstructorIs(55L);
        ExamBoardManager examBoardManager = examBoardManagerReturningBoard(3L);

        ExamBoardResponse response = readExamBoardAs(examBoardManager, "ROLE_DOCENTE", "ROLE_ALUMNO");

        assertThat(response.id()).isEqualTo(3L);
        verify(examBoardManager).requireTribunalMember(3L, 55L);
    }

    @Test
    void teacherWithStudentRoleOutsideTheTribunalIsRejected() {
        currentInstructorIs(55L);
        ExamBoardManager examBoardManager = examBoardManagerReturningBoard(3L);
        doThrow(new NotExamBoardTribunalMemberException(55L, 3L)).when(examBoardManager).requireTribunalMember(3L, 55L);

        assertThatThrownBy(() -> readExamBoardAs(examBoardManager, "ROLE_DOCENTE", "ROLE_ALUMNO"))
                .isInstanceOf(NotExamBoardTribunalMemberException.class);
        verify(examBoardManager, never()).get(any());
    }

    @Test
    void staffWithoutTeacherRoleSkipsTribunalCheckOnExamBoard() {
        ExamBoardManager examBoardManager = examBoardManagerReturningBoard(3L);

        ExamBoardResponse response = readExamBoardAs(examBoardManager, "ROLE_DIRECTOR");

        assertThat(response.id()).isEqualTo(3L);
        verify(examBoardManager, never()).requireTribunalMember(any(), any());
    }
}
