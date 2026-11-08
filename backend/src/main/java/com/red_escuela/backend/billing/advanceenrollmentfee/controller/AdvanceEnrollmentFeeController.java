package com.red_escuela.backend.billing.advanceenrollmentfee.controller;

import com.red_escuela.backend.billing.advanceenrollmentfee.dto.AdvanceEnrollmentFeeResponse;
import com.red_escuela.backend.billing.advanceenrollmentfee.usecase.RequestAdvanceEnrollmentFeeUseCase;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.RequiresModule;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiresModule(RequiresModule.BILLING)
public class AdvanceEnrollmentFeeController {
    private final RequestAdvanceEnrollmentFeeUseCase requestUseCase;

    public AdvanceEnrollmentFeeController(RequestAdvanceEnrollmentFeeUseCase requestUseCase) {
        this.requestUseCase = requestUseCase;
    }

    @RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.FULL)
    @PostMapping("/me/program-enrollments/{id}/advance-enrollment-fee")
    @PreAuthorize("hasRole('ALUMNO')")
    public AdvanceEnrollmentFeeResponse requestForMe(@PathVariable Long id) {
        return AdvanceEnrollmentFeeResponse.from(requestUseCase.requestForCurrentStudent(id));
    }

    @RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.FULL)
    @PostMapping("/program-enrollments/{id}/advance-enrollment-fee")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public AdvanceEnrollmentFeeResponse requestForStudent(@PathVariable Long id) {
        return AdvanceEnrollmentFeeResponse.from(requestUseCase.requestForStudentAtCounter(id));
    }
}
