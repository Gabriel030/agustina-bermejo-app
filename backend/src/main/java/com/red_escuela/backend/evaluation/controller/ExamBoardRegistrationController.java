package com.red_escuela.backend.evaluation.controller;

import com.red_escuela.backend.evaluation.dto.CreateExamBoardRegistrationRequest;
import com.red_escuela.backend.evaluation.dto.ExamBoardRegistrationResponse;
import com.red_escuela.backend.evaluation.specialist.ExamBoardRegistrationManager;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExamBoardRegistrationController {

    private final ExamBoardRegistrationManager examBoardRegistrationService;

    public ExamBoardRegistrationController(ExamBoardRegistrationManager examBoardRegistrationService) {
        this.examBoardRegistrationService = examBoardRegistrationService;
    }

    @PostMapping("/exam-boards/{id}/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(code = "evaluation.manage_exam_boards", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO")
    public ExamBoardRegistrationResponse register(@PathVariable Long id, @Valid @RequestBody CreateExamBoardRegistrationRequest request) {
        boolean hasProgram = request.programSectionEnrollmentId() != null;
        boolean hasCourse = request.courseSectionEnrollmentId() != null;
        if (hasProgram == hasCourse) {
            throw new IllegalArgumentException(
                    "La inscripción tiene que referenciar exactamente una cursada: programSectionEnrollmentId O courseSectionEnrollmentId");
        }
        var registration = hasProgram
                ? examBoardRegistrationService.registerProgramEnrollment(id, request.programSectionEnrollmentId())
                : examBoardRegistrationService.registerCourseEnrollment(id, request.courseSectionEnrollmentId());
        return ExamBoardRegistrationResponse.from(registration);
    }

    @PostMapping("/exam-board-registrations/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(code = "evaluation.manage_exam_boards", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO")
    public void withdraw(@PathVariable Long id) {
        examBoardRegistrationService.withdraw(id);
    }
}
