package com.red_escuela.backend.academicrecord.controller;

import com.red_escuela.backend.academicrecord.dto.StudentAttendanceResponse;
import com.red_escuela.backend.academicrecord.dto.StudentAttendanceRiskResponse;
import com.red_escuela.backend.academicrecord.dto.StudentCourseSectionEnrollmentResponse;
import com.red_escuela.backend.academicrecord.dto.StudentGradeItemResponse;
import com.red_escuela.backend.academicrecord.usecase.studentrecord.ListMyCourseEnrollmentAttendanceUseCase;
import com.red_escuela.backend.academicrecord.usecase.studentrecord.ListMyCourseEnrollmentGradeItemsUseCase;
import com.red_escuela.backend.academicrecord.usecase.studentrecord.ListMyCourseSectionEnrollmentsUseCase;
import com.red_escuela.backend.academicrecord.usecase.studentrecord.ReadMyCourseEnrollmentAttendanceRiskUseCase;
import com.red_escuela.backend.academicrecord.usecase.studentrecord.ReadMyCourseSectionEnrollmentUseCase;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/me/course-section-enrollments")
public class MeCourseSectionEnrollmentController {
    private final CurrentStudentService currentStudentService;
    private final ListMyCourseSectionEnrollmentsUseCase listMyCourseSectionEnrollmentsUseCase;
    private final ReadMyCourseSectionEnrollmentUseCase readMyCourseSectionEnrollmentUseCase;
    private final ListMyCourseEnrollmentAttendanceUseCase listMyCourseEnrollmentAttendanceUseCase;
    private final ReadMyCourseEnrollmentAttendanceRiskUseCase readMyCourseEnrollmentAttendanceRiskUseCase;
    private final ListMyCourseEnrollmentGradeItemsUseCase listMyCourseEnrollmentGradeItemsUseCase;

    public MeCourseSectionEnrollmentController(
            CurrentStudentService currentStudentService,
            ListMyCourseSectionEnrollmentsUseCase listMyCourseSectionEnrollmentsUseCase,
            ReadMyCourseSectionEnrollmentUseCase readMyCourseSectionEnrollmentUseCase,
            ListMyCourseEnrollmentAttendanceUseCase listMyCourseEnrollmentAttendanceUseCase,
            ReadMyCourseEnrollmentAttendanceRiskUseCase readMyCourseEnrollmentAttendanceRiskUseCase,
            ListMyCourseEnrollmentGradeItemsUseCase listMyCourseEnrollmentGradeItemsUseCase) {
        this.currentStudentService = currentStudentService;
        this.listMyCourseSectionEnrollmentsUseCase = listMyCourseSectionEnrollmentsUseCase;
        this.readMyCourseSectionEnrollmentUseCase = readMyCourseSectionEnrollmentUseCase;
        this.listMyCourseEnrollmentAttendanceUseCase = listMyCourseEnrollmentAttendanceUseCase;
        this.readMyCourseEnrollmentAttendanceRiskUseCase = readMyCourseEnrollmentAttendanceRiskUseCase;
        this.listMyCourseEnrollmentGradeItemsUseCase = listMyCourseEnrollmentGradeItemsUseCase;
    }

    @GetMapping
    public List<StudentCourseSectionEnrollmentResponse> mine(@RequestParam(required = false) Long courseEnrollmentId) {
        return listMyCourseSectionEnrollmentsUseCase.list(currentStudentService.resolveCurrentStudent().getId(), courseEnrollmentId);
    }

    @GetMapping("/{id}")
    public StudentCourseSectionEnrollmentResponse mineById(@PathVariable Long id) {
        return readMyCourseSectionEnrollmentUseCase.read(currentStudentService.resolveCurrentStudent().getId(), id);
    }

    @GetMapping("/{id}/attendance")
    public List<StudentAttendanceResponse> myAttendance(@PathVariable Long id) {
        return listMyCourseEnrollmentAttendanceUseCase.list(currentStudentService.resolveCurrentStudent().getId(), id);
    }

    @GetMapping("/{id}/attendance/risk")
    public StudentAttendanceRiskResponse myAttendanceRisk(@PathVariable Long id) {
        return readMyCourseEnrollmentAttendanceRiskUseCase.read(currentStudentService.resolveCurrentStudent().getId(), id);
    }

    @GetMapping("/{id}/grade-items")
    public List<StudentGradeItemResponse> myGrades(@PathVariable Long id) {
        return listMyCourseEnrollmentGradeItemsUseCase.list(currentStudentService.resolveCurrentStudent().getId(), id);
    }
}
