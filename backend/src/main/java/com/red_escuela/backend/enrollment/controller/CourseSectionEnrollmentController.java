package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.CourseSectionEnrollmentResponse;
import com.red_escuela.backend.enrollment.service.CourseSectionEnrollmentService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Sin alta manual: el alta de CourseSubjectSectionEnrollment es exclusivamente
// automática desde CourseEnrollmentController.matricular (decisión 15).
@RestController
public class CourseSectionEnrollmentController {

    private final CourseSectionEnrollmentService courseSectionEnrollmentService;

    public CourseSectionEnrollmentController(CourseSectionEnrollmentService courseSectionEnrollmentService) {
        this.courseSectionEnrollmentService = courseSectionEnrollmentService;
    }

    @PostMapping("/course-section-enrollments/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void darDeBaja(@PathVariable Long id) {
        courseSectionEnrollmentService.darDeBaja(id);
    }

    @GetMapping("/course-enrollments/{id}/section-enrollments")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<CourseSectionEnrollmentResponse> listarPorMatriculacion(@PathVariable Long id) {
        return courseSectionEnrollmentService.listarPorMatriculacion(id).stream()
                .map(CourseSectionEnrollmentResponse::from).toList();
    }
}
