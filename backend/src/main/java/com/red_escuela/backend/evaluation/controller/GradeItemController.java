package com.red_escuela.backend.evaluation.controller;

import com.red_escuela.backend.evaluation.dto.CreateGradeItemRequest;
import com.red_escuela.backend.evaluation.dto.GradeItemResponse;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.service.CurrentInstructorService;
import com.red_escuela.backend.evaluation.service.GradeItemService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class GradeItemController {

    private final GradeItemService gradeItemService;
    private final CurrentInstructorService currentInstructorService;

    public GradeItemController(GradeItemService gradeItemService, CurrentInstructorService currentInstructorService) {
        this.gradeItemService = gradeItemService;
        this.currentInstructorService = currentInstructorService;
    }

    @PostMapping("/section-enrollments/{id}/grade-items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DOCENTE')")
    public GradeItemResponse loadProgramEnrollmentGrade(@PathVariable Long id, @Valid @RequestBody CreateGradeItemRequest request) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return GradeItemResponse.from(gradeItemService.loadProgramEnrollmentGrade(id, instructor.getId(), request));
    }

    @PostMapping("/course-section-enrollments/{id}/grade-items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DOCENTE')")
    public GradeItemResponse loadCourseEnrollmentGrade(@PathVariable Long id, @Valid @RequestBody CreateGradeItemRequest request) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return GradeItemResponse.from(gradeItemService.loadCourseEnrollmentGrade(id, instructor.getId(), request));
    }

    @GetMapping("/section-enrollments/{id}/grade-items")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<GradeItemResponse> listProgramEnrollmentGrades(@PathVariable Long id) {
        return gradeItemService.listByProgramEnrollment(id).stream().map(GradeItemResponse::from).toList();
    }

    @GetMapping("/course-section-enrollments/{id}/grade-items")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<GradeItemResponse> listCourseEnrollmentGrades(@PathVariable Long id) {
        return gradeItemService.listByCourseEnrollment(id).stream().map(GradeItemResponse::from).toList();
    }
}
