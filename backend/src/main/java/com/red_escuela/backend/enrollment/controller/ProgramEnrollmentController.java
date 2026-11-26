package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.billing.dto.AdvanceBillingYearRequest;
import com.red_escuela.backend.enrollment.dto.CreateProgramEnrollmentRequest;
import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentResponse;
import com.red_escuela.backend.enrollment.dto.ProgramProgressResponse;
import com.red_escuela.backend.enrollment.dto.SubjectAttendanceSummaryResponse;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.identity.specialist.CurrentPersonService;
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
public class ProgramEnrollmentController {
    private final ProgramEnrollmentService programEnrollmentService;
    private final CurrentPersonService currentPersonService;

    public ProgramEnrollmentController(ProgramEnrollmentService programEnrollmentService, CurrentPersonService currentPersonService) {
        this.programEnrollmentService = programEnrollmentService;
        this.currentPersonService = currentPersonService;
    }

    @PostMapping("/students/{id}/program-enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(code = "enrollment.create_program_enrollment", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public ProgramEnrollmentResponse enroll(@PathVariable Long id, @Valid @RequestBody CreateProgramEnrollmentRequest request) {
        return ProgramEnrollmentResponse.from(programEnrollmentService.enroll(id, request));
    }

    @GetMapping("/students/{id}/program-enrollments")
    @RequiresPermission(code = "enrollment.read_program_enrollments", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public List<ProgramEnrollmentResponse> listByStudent(@PathVariable Long id) {
        return programEnrollmentService.listByStudent(id).stream().map(ProgramEnrollmentResponse::from).toList();
    }

    @GetMapping("/program-enrollments/{id}")
    @RequiresPermission(code = "enrollment.read_program_enrollments", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public ProgramEnrollmentResponse get(@PathVariable Long id) {
        return ProgramEnrollmentResponse.from(programEnrollmentService.get(id));
    }

    @GetMapping("/program-enrollments/{id}/progress")
    @RequiresPermission(code = "enrollment.read_program_enrollments", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public ProgramProgressResponse getProgress(@PathVariable Long id) {
        return programEnrollmentService.calculateProgress(id);
    }

    @GetMapping("/program-enrollments/{id}/attendance-summary")
    @RequiresPermission(code = "enrollment.read_program_enrollments", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public List<SubjectAttendanceSummaryResponse> getAttendanceSummary(@PathVariable Long id) {
        return programEnrollmentService.attendanceSummaryBySubject(id);
    }

    @PostMapping("/program-enrollments/{id}/advance-billing-year")
    @RequiresPermission(code = "enrollment.advance_billing_year", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO")
    public ProgramEnrollmentResponse advanceAcademicYear(@PathVariable Long id, @Valid @RequestBody AdvanceBillingYearRequest request) {
        return ProgramEnrollmentResponse.from(programEnrollmentService.advanceAcademicYear(id, request.templateId()));
    }
}
