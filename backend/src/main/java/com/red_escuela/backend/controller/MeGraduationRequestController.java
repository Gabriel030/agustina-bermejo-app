package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.GraduationRequestResponse;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.service.CurrentStudentService;
import com.red_escuela.backend.service.GraduationRequestService;
import com.red_escuela.backend.service.ProgramEnrollmentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** RF-AL-15: autoservicio del alumno para solicitar el trámite de egreso de su propia carrera. */
@RestController
@RequestMapping("/me/program-enrollments/{id}/graduation-request")
public class MeGraduationRequestController {

    private final CurrentStudentService currentStudentService;
    private final ProgramEnrollmentService programEnrollmentService;
    private final GraduationRequestService graduationRequestService;

    public MeGraduationRequestController(
            CurrentStudentService currentStudentService, ProgramEnrollmentService programEnrollmentService,
            GraduationRequestService graduationRequestService) {
        this.currentStudentService = currentStudentService;
        this.programEnrollmentService = programEnrollmentService;
        this.graduationRequestService = graduationRequestService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GraduationRequestResponse solicitar(@PathVariable Long id) {
        Student student = currentStudentService.resolveCurrentStudent();
        ProgramEnrollment enrollment = programEnrollmentService.verDetalle(id);
        if (!enrollment.getStudent().getId().equals(student.getId())) {
            throw new ProgramEnrollmentNotFoundException(id);
        }
        return GraduationRequestResponse.from(graduationRequestService.solicitar(id));
    }
}
