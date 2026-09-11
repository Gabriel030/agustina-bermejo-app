package com.red_escuela.backend.evaluation.controller;

import com.red_escuela.backend.evaluation.dto.CreateExamBoardRegistrationRequest;
import com.red_escuela.backend.evaluation.dto.ExamBoardRegistrationResponse;
import com.red_escuela.backend.evaluation.service.ExamBoardRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** RF-PR-09: gestión de inscripciones a mesas de examen por Preceptor/Administrativo. */
@RestController
public class ExamBoardRegistrationController {

    private final ExamBoardRegistrationService examBoardRegistrationService;

    public ExamBoardRegistrationController(ExamBoardRegistrationService examBoardRegistrationService) {
        this.examBoardRegistrationService = examBoardRegistrationService;
    }

    @PostMapping("/exam-boards/{id}/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public ExamBoardRegistrationResponse inscribir(@PathVariable Long id, @Valid @RequestBody CreateExamBoardRegistrationRequest request) {
        boolean hasProgram = request.programSectionEnrollmentId() != null;
        boolean hasCourse = request.courseSectionEnrollmentId() != null;
        if (hasProgram == hasCourse) {
            throw new IllegalArgumentException(
                    "La inscripción tiene que referenciar exactamente una cursada: programSectionEnrollmentId O courseSectionEnrollmentId");
        }
        var registration = hasProgram
                ? examBoardRegistrationService.inscribirProgramEnrollment(id, request.programSectionEnrollmentId())
                : examBoardRegistrationService.inscribirCourseEnrollment(id, request.courseSectionEnrollmentId());
        return ExamBoardRegistrationResponse.from(registration);
    }

    @PostMapping("/exam-board-registrations/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void darDeBaja(@PathVariable Long id) {
        examBoardRegistrationService.darDeBaja(id);
    }
}
