package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.billing.dto.AdvanceBillingYearRequest;
import com.red_escuela.backend.billing.dto.CreateAdHocInvoiceRequest;
import com.red_escuela.backend.billing.dto.InvoiceResponse;
import com.red_escuela.backend.billing.usecase.invoice.CreateAdHocInvoiceForProgramEnrollmentUseCase;
import com.red_escuela.backend.enrollment.dto.CreateProgramEnrollmentRequest;
import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentResponse;
import com.red_escuela.backend.enrollment.dto.ProgramProgressResponse;
import com.red_escuela.backend.enrollment.dto.SubjectAttendanceSummaryResponse;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
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
public class ProgramEnrollmentController {
    private final ProgramEnrollmentService programEnrollmentService;
    private final CreateAdHocInvoiceForProgramEnrollmentUseCase createAdHocInvoiceUseCase;

    public ProgramEnrollmentController(ProgramEnrollmentService programEnrollmentService, CreateAdHocInvoiceForProgramEnrollmentUseCase createAdHocInvoiceUseCase) {
        this.programEnrollmentService = programEnrollmentService;
        this.createAdHocInvoiceUseCase = createAdHocInvoiceUseCase;
    }

    @PostMapping("/students/{id}/program-enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public ProgramEnrollmentResponse enroll(@PathVariable Long id, @Valid @RequestBody CreateProgramEnrollmentRequest request) {
        return ProgramEnrollmentResponse.from(programEnrollmentService.enroll(id, request));
    }

    @PostMapping("/program-enrollments/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void withdraw(@PathVariable Long id) {
        programEnrollmentService.withdraw(id);
    }

    @GetMapping("/students/{id}/program-enrollments")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<ProgramEnrollmentResponse> listByStudent(@PathVariable Long id) {
        return programEnrollmentService.listByStudent(id).stream().map(ProgramEnrollmentResponse::from).toList();
    }

    @GetMapping("/program-enrollments/{id}")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public ProgramEnrollmentResponse get(@PathVariable Long id) {
        return ProgramEnrollmentResponse.from(programEnrollmentService.get(id));
    }

    @GetMapping("/program-enrollments/{id}/progress")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public ProgramProgressResponse getProgress(@PathVariable Long id) {
        return programEnrollmentService.calculateProgress(id);
    }

    @GetMapping("/program-enrollments/{id}/attendance-summary")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<SubjectAttendanceSummaryResponse> getAttendanceSummary(@PathVariable Long id) {
        return programEnrollmentService.attendanceSummaryBySubject(id);
    }

    @PostMapping("/program-enrollments/{id}/advance-billing-year")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public ProgramEnrollmentResponse advanceAcademicYear(@PathVariable Long id, @Valid @RequestBody AdvanceBillingYearRequest request) {
        return ProgramEnrollmentResponse.from(programEnrollmentService.advanceAcademicYear(id, request.templateId()));
    }

    @PostMapping("/program-enrollments/{id}/invoices/ad-hoc")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public InvoiceResponse createAdHocInvoice(@PathVariable Long id, @Valid @RequestBody CreateAdHocInvoiceRequest request) {
        return InvoiceResponse.from(createAdHocInvoiceUseCase.create(id, request));
    }
}
