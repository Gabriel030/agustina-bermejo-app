package com.red_escuela.backend.evaluation.controller;

import com.red_escuela.backend.evaluation.specialist.ExamBoardManager;
import com.red_escuela.backend.evaluation.specialist.ExamBoardRegistrationManager;
import com.red_escuela.backend.evaluation.specialist.GradeItemRecorder;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import com.red_escuela.backend.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MeExamBoardRegistrationControllerTest {

    private ExamBoardRegistrationManager registrationManager;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CurrentStudentService currentStudentService = mock(CurrentStudentService.class);
        Student student = new Student();
        student.setId(7L);
        when(currentStudentService.resolveCurrentStudent()).thenReturn(student);
        registrationManager = mock(ExamBoardRegistrationManager.class);
        when(registrationManager.registerProgramEnrollmentOfStudent(any(), any(), isNull()))
                .thenThrow(new InvalidDataAccessApiUsageException("The given id must not be null"));
        mockMvc = MockMvcBuilders.standaloneSetup(new MeExamBoardRegistrationController(
                        currentStudentService, registrationManager, mock(ExamBoardManager.class), mock(GradeItemRecorder.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void registrationWithoutProgramSectionEnrollmentIsABadRequestAndNeverReachesTheManager() throws Exception {
        mockMvc.perform(post("/me/exam-board-registrations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"examBoardId\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ILLEGAL_ARGUMENT"));

        verifyNoInteractions(registrationManager);
    }

    @Test
    void registrationWithoutExamBoardIsStillRejectedByBeanValidation() throws Exception {
        mockMvc.perform(post("/me/exam-board-registrations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"programSectionEnrollmentId\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void registrationWithBothIdsIsDelegatedToTheManagerWithTheCurrentStudent() throws Exception {
        mockMvc.perform(post("/me/exam-board-registrations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"examBoardId\":5,\"programSectionEnrollmentId\":9}"));

        verify(registrationManager).registerProgramEnrollmentOfStudent(7L, 5L, 9L);
    }
}
