package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.MyCourseEnrollmentResponse;
import com.red_escuela.backend.enrollment.usecase.courseenrollment.ListMyCourseEnrollmentsUseCase;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/me")
public class MeCourseEnrollmentController {
    private final CurrentStudentService currentStudentService;
    private final ListMyCourseEnrollmentsUseCase listMyCourseEnrollmentsUseCase;

    public MeCourseEnrollmentController(
            CurrentStudentService currentStudentService, ListMyCourseEnrollmentsUseCase listMyCourseEnrollmentsUseCase) {
        this.currentStudentService = currentStudentService;
        this.listMyCourseEnrollmentsUseCase = listMyCourseEnrollmentsUseCase;
    }

    @GetMapping("/course-enrollments")
    public List<MyCourseEnrollmentResponse> mine() {
        return listMyCourseEnrollmentsUseCase.list(currentStudentService.resolveCurrentStudent().getId());
    }
}
