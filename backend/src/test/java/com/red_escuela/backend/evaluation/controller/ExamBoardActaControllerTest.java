package com.red_escuela.backend.evaluation.controller;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.service.CurrentInstructorService;
import com.red_escuela.backend.evaluation.dto.ExamBoardActaFile;
import com.red_escuela.backend.evaluation.exception.ExamBoardCanceledException;
import com.red_escuela.backend.evaluation.exception.NotExamBoardTribunalMemberException;
import com.red_escuela.backend.evaluation.usecase.examboard.GenerateExamBoardActaCommand;
import com.red_escuela.backend.evaluation.usecase.examboard.GenerateExamBoardActaUseCase;
import com.red_escuela.backend.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExamBoardActaControllerTest {

    private GenerateExamBoardActaUseCase useCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCase = mock(GenerateExamBoardActaUseCase.class);
        CurrentInstructorService instructors = mock(CurrentInstructorService.class);
        Instructor instructor = new Instructor();
        instructor.setId(9L);
        when(instructors.resolveCurrentInstructor()).thenReturn(instructor);
        mockMvc = MockMvcBuilders.standaloneSetup(new ExamBoardActaController(instructors, useCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Principal as(String role) {
        return new UsernamePasswordAuthenticationToken("user", "n/a", java.util.List.of(new SimpleGrantedAuthority(role)));
    }

    @Test
    void tribunalMemberDownloadsThePdf() throws Exception {
        when(useCase.generate(new GenerateExamBoardActaCommand(5L, 9L))).thenReturn(new ExamBoardActaFile("acta-mesa-5.pdf", new byte[] {1, 2, 3}));

        mockMvc.perform(get("/exam-boards/5/acta").principal(as("ROLE_DOCENTE")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("acta-mesa-5.pdf")))
                .andExpect(content().bytes(new byte[] {1, 2, 3}));
            }

    @Test
    void teacherOutsideTheTribunalIsRejectedLikeTheOtherTribunalEndpointsAndNoPdfIsGenerated() throws Exception {
        when(useCase.generate(new GenerateExamBoardActaCommand(5L, 9L))).thenThrow(new NotExamBoardTribunalMemberException(9L, 5L));

        mockMvc.perform(get("/exam-boards/5/acta").principal(as("ROLE_DOCENTE")))
                .andExpect(status().isNotFound());
            }

    @Test
    void directorSkipsTheTribunalCheck() throws Exception {
        when(useCase.generate(new GenerateExamBoardActaCommand(5L, null))).thenReturn(new ExamBoardActaFile("acta-mesa-5.pdf", new byte[] {1}));

        mockMvc.perform(get("/exam-boards/5/acta").principal(as("ROLE_DIRECTOR"))).andExpect(status().isOk());
    }

    @Test
    void canceledBoardIsAConflictByCode() throws Exception {
        when(useCase.generate(new GenerateExamBoardActaCommand(5L, null))).thenThrow(new ExamBoardCanceledException(5L));

        mockMvc.perform(get("/exam-boards/5/acta").principal(as("ROLE_ADMINISTRATIVO")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAM_BOARD_CANCELED"));
    }
}
