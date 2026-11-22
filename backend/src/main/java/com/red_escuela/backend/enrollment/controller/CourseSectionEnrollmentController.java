package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.CourseSectionEnrollmentResponse;
import com.red_escuela.backend.enrollment.service.CourseSectionEnrollmentService;
import org.springframework.http.HttpStatus;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CourseSectionEnrollmentController {
    private final CourseSectionEnrollmentService courseSectionEnrollmentService;

    public CourseSectionEnrollmentController(CourseSectionEnrollmentService courseSectionEnrollmentService) {
        this.courseSectionEnrollmentService = courseSectionEnrollmentService;
    }

    @GetMapping("/course-enrollments/{id}/section-enrollments")
    @RequiresPermission(code = "enrollment.read_course_enrollments", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public List<CourseSectionEnrollmentResponse> listByEnrollment(@PathVariable Long id) {
        return courseSectionEnrollmentService.listByEnrollment(id).stream()
                .map(CourseSectionEnrollmentResponse::from).toList();
    }
}
