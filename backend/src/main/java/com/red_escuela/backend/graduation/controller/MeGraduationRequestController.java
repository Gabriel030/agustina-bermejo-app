package com.red_escuela.backend.graduation.controller;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.graduation.dto.GraduationRequestResponse;
import com.red_escuela.backend.graduation.usecase.graduationrequest.RequestGraduationUseCase;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me/program-enrollments/{id}/graduation-request")
public class MeGraduationRequestController {

    private final CurrentStudentService currentStudentService;
    private final ProgramEnrollmentService programEnrollmentService;
    private final RequestGraduationUseCase requestGraduationUseCase;

    public MeGraduationRequestController(
            CurrentStudentService currentStudentService, ProgramEnrollmentService programEnrollmentService,
            RequestGraduationUseCase requestGraduationUseCase) {
        this.currentStudentService = currentStudentService;
        this.programEnrollmentService = programEnrollmentService;
        this.requestGraduationUseCase = requestGraduationUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GraduationRequestResponse request(@PathVariable Long id) {
        Student student = currentStudentService.resolveCurrentStudent();
        ProgramEnrollment enrollment = programEnrollmentService.get(id);
        if (!enrollment.getStudent().getId().equals(student.getId())) {
            throw new ProgramEnrollmentNotFoundException(id);
        }
        return GraduationRequestResponse.from(requestGraduationUseCase.request(id));
    }
}
