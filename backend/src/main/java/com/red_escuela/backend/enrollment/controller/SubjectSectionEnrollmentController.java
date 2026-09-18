package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.ChangeSectionRequest;
import com.red_escuela.backend.enrollment.dto.CreateSectionEnrollmentRequest;
import com.red_escuela.backend.enrollment.dto.SectionEnrollmentResponse;
import com.red_escuela.backend.enrollment.service.SubjectSectionEnrollmentService;
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
public class SubjectSectionEnrollmentController {

    private final SubjectSectionEnrollmentService subjectSectionEnrollmentService;

    public SubjectSectionEnrollmentController(SubjectSectionEnrollmentService subjectSectionEnrollmentService) {
        this.subjectSectionEnrollmentService = subjectSectionEnrollmentService;
    }

    @PostMapping("/program-enrollments/{id}/section-enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public SectionEnrollmentResponse enroll(@PathVariable Long id, @Valid @RequestBody CreateSectionEnrollmentRequest request) {
        return SectionEnrollmentResponse.from(subjectSectionEnrollmentService.enroll(id, request));
    }

    @PostMapping("/section-enrollments/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void withdraw(@PathVariable Long id) {
        subjectSectionEnrollmentService.withdraw(id);
    }

    @PostMapping("/section-enrollments/{id}/change-section")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public SectionEnrollmentResponse changeSection(@PathVariable Long id, @Valid @RequestBody ChangeSectionRequest request) {
        return SectionEnrollmentResponse.from(subjectSectionEnrollmentService.changeSection(id, request));
    }

    @GetMapping("/program-enrollments/{id}/section-enrollments")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<SectionEnrollmentResponse> listByEnrollment(@PathVariable Long id) {
        return subjectSectionEnrollmentService.listByEnrollment(id).stream().map(SectionEnrollmentResponse::from).toList();
    }
}
