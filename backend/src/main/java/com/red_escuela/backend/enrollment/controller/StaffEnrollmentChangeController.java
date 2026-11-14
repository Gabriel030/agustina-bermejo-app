package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.ChangeSectionCandidateResponse;
import com.red_escuela.backend.enrollment.dto.ChangeSectionRequest;
import com.red_escuela.backend.enrollment.dto.SectionEnrollmentResponse;
import com.red_escuela.backend.enrollment.dto.WithdrawEnrollmentRequest;
import com.red_escuela.backend.enrollment.dto.WithdrawSectionEnrollmentRequest;
import com.red_escuela.backend.enrollment.usecase.StaffEnrollmentChange;
import io.swagger.v3.oas.annotations.Operation;
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
public class StaffEnrollmentChangeController {

    private final StaffEnrollmentChange staffEnrollmentChange;

    public StaffEnrollmentChangeController(StaffEnrollmentChange staffEnrollmentChange) {
        this.staffEnrollmentChange = staffEnrollmentChange;
    }

    @GetMapping("/section-enrollments/{id}/change-section-candidates")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<ChangeSectionCandidateResponse> listChangeSectionCandidates(@PathVariable Long id) {
        return staffEnrollmentChange.listChangeSectionCandidates(id);
    }

    @Operation(operationId = "SubjectSectionEnrollment_withdraw")
    @PostMapping("/section-enrollments/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public void withdrawSubject(@PathVariable Long id, @Valid @RequestBody(required = false) WithdrawSectionEnrollmentRequest request) {
        staffEnrollmentChange.withdrawSubject(id, request == null ? null : request.reason());
    }

    @Operation(operationId = "SubjectSectionEnrollment_changeSection")
    @PostMapping("/section-enrollments/{id}/change-section")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public SectionEnrollmentResponse changeSubjectSection(@PathVariable Long id, @Valid @RequestBody ChangeSectionRequest request) {
        return staffEnrollmentChange.changeSubjectSection(id, request);
    }

    @Operation(operationId = "CourseSectionEnrollment_withdraw")
    @PostMapping("/course-section-enrollments/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public void withdrawCourseModule(@PathVariable Long id, @Valid @RequestBody(required = false) WithdrawSectionEnrollmentRequest request) {
        staffEnrollmentChange.withdrawCourseModule(id, request == null ? null : request.reason());
    }

    @Operation(operationId = "ProgramEnrollment_withdraw")
    @PostMapping("/program-enrollments/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public void withdrawProgram(@PathVariable Long id, @Valid @RequestBody(required = false) WithdrawEnrollmentRequest request) {
        staffEnrollmentChange.withdrawProgram(id, request);
    }

    @Operation(operationId = "CourseEnrollment_withdraw")
    @PostMapping("/course-enrollments/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public void withdrawCourse(@PathVariable Long id, @Valid @RequestBody(required = false) WithdrawEnrollmentRequest request) {
        staffEnrollmentChange.withdrawCourse(id, request);
    }
}
