package com.red_escuela.backend.billing.reporting.controller;

import com.red_escuela.backend.billing.dto.AdminEnrollmentBillingStatusResponse;
import com.red_escuela.backend.billing.usecase.invoice.GetStudentBillingOverviewUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/students")
@Tag(name = "student-controller")
public class StudentBillingOverviewController {
    private final GetStudentBillingOverviewUseCase getStudentBillingOverviewUseCase;

    public StudentBillingOverviewController(GetStudentBillingOverviewUseCase getStudentBillingOverviewUseCase) {
        this.getStudentBillingOverviewUseCase = getStudentBillingOverviewUseCase;
    }

    @GetMapping("/{id}/billing-overview")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @Operation(operationId = "Student_billingOverview")
    public List<AdminEnrollmentBillingStatusResponse> billingOverview(@PathVariable Long id) {
        return getStudentBillingOverviewUseCase.get(id);
    }
}
