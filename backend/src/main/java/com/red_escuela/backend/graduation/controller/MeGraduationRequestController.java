package com.red_escuela.backend.graduation.controller;

import com.red_escuela.backend.graduation.dto.GraduationRequestResponse;
import com.red_escuela.backend.graduation.dto.OwnGraduationRequestResponse;
import com.red_escuela.backend.graduation.usecase.graduationrequest.GetOwnGraduationRequestUseCase;
import com.red_escuela.backend.graduation.usecase.graduationrequest.RequestGraduationUseCase;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.graduation.usecase.graduationrequest.VerifyProgramEnrollmentOwnerUseCase;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me/program-enrollments/{id}/graduation-request")
public class MeGraduationRequestController {

    private final CurrentStudentService currentStudentService;
    private final VerifyProgramEnrollmentOwnerUseCase verifyProgramEnrollmentOwnerUseCase;
    private final RequestGraduationUseCase requestGraduationUseCase;
    private final GetOwnGraduationRequestUseCase getOwnGraduationRequestUseCase;

    public MeGraduationRequestController(
            CurrentStudentService currentStudentService, VerifyProgramEnrollmentOwnerUseCase verifyProgramEnrollmentOwnerUseCase,
            RequestGraduationUseCase requestGraduationUseCase, GetOwnGraduationRequestUseCase getOwnGraduationRequestUseCase) {
        this.currentStudentService = currentStudentService;
        this.verifyProgramEnrollmentOwnerUseCase = verifyProgramEnrollmentOwnerUseCase;
        this.requestGraduationUseCase = requestGraduationUseCase;
        this.getOwnGraduationRequestUseCase = getOwnGraduationRequestUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GraduationRequestResponse request(@PathVariable Long id) {
        Student student = currentStudentService.resolveCurrentStudent();
        verifyProgramEnrollmentOwnerUseCase.verify(id, student.getId());
        return GraduationRequestResponse.from(requestGraduationUseCase.request(id));
    }

    @GetMapping
    public OwnGraduationRequestResponse get(@PathVariable Long id) {
        Student student = currentStudentService.resolveCurrentStudent();
        verifyProgramEnrollmentOwnerUseCase.verify(id, student.getId());
        return getOwnGraduationRequestUseCase.get(id);
    }
}
