package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.CreateSectionEnrollmentRequest;
import com.red_escuela.backend.enrollment.dto.SectionEnrollmentResponse;
import com.red_escuela.backend.enrollment.service.SubjectSectionEnrollmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SubjectSectionEnrollmentController {

    private final SubjectSectionEnrollmentService subjectSectionEnrollmentService;

    public SubjectSectionEnrollmentController(SubjectSectionEnrollmentService subjectSectionEnrollmentService) {
        this.subjectSectionEnrollmentService = subjectSectionEnrollmentService;
    }

    @PostMapping("/program-enrollments/{id}/section-enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(code = "enrollment.enroll_in_subject_section", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public SectionEnrollmentResponse enroll(@PathVariable Long id, @Valid @RequestBody CreateSectionEnrollmentRequest request) {
        return SectionEnrollmentResponse.from(subjectSectionEnrollmentService.enroll(id, request));
    }

    @GetMapping("/program-enrollments/{id}/section-enrollments")
    @RequiresPermission(code = "enrollment.read_program_enrollments", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public List<SectionEnrollmentResponse> listByEnrollment(@PathVariable Long id) {
        return subjectSectionEnrollmentService.listByEnrollment(id).stream().map(SectionEnrollmentResponse::from).toList();
    }
}
