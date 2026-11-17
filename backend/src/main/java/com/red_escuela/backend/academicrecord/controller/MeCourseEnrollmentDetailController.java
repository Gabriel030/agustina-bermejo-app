package com.red_escuela.backend.academicrecord.controller;

import com.red_escuela.backend.academicrecord.dto.MyCourseEnrollmentDetailResponse;
import com.red_escuela.backend.academicrecord.usecase.ReadMyCourseEnrollmentDetailUseCase;
import com.red_escuela.backend.identity.specialist.CurrentStudentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me/course-enrollments")
public class MeCourseEnrollmentDetailController {
    private final CurrentStudentService currentStudentService;
    private final ReadMyCourseEnrollmentDetailUseCase readMyCourseEnrollmentDetailUseCase;

    public MeCourseEnrollmentDetailController(
            CurrentStudentService currentStudentService, ReadMyCourseEnrollmentDetailUseCase readMyCourseEnrollmentDetailUseCase) {
        this.currentStudentService = currentStudentService;
        this.readMyCourseEnrollmentDetailUseCase = readMyCourseEnrollmentDetailUseCase;
    }

    @GetMapping("/{id}")
    public MyCourseEnrollmentDetailResponse mineById(@PathVariable Long id) {
        return readMyCourseEnrollmentDetailUseCase.read(currentStudentService.resolveCurrentStudent().getId(), id);
    }
}
