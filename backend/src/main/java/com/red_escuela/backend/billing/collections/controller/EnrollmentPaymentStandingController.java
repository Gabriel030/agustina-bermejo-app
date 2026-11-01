package com.red_escuela.backend.billing.collections.controller;

import com.red_escuela.backend.billing.collections.domain.EnrollmentKind;
import com.red_escuela.backend.billing.collections.dto.EnrollmentPaymentStandingResponse;
import com.red_escuela.backend.billing.collections.dto.EnrollmentPaymentStatusChangeResponse;
import com.red_escuela.backend.billing.collections.usecase.GetEnrollmentPaymentStandingUseCase;
import com.red_escuela.backend.shared.module.RequiresModule;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiresModule(RequiresModule.BILLING)
public class EnrollmentPaymentStandingController {
    private final GetEnrollmentPaymentStandingUseCase getEnrollmentPaymentStandingUseCase;

    public EnrollmentPaymentStandingController(GetEnrollmentPaymentStandingUseCase getEnrollmentPaymentStandingUseCase) {
        this.getEnrollmentPaymentStandingUseCase = getEnrollmentPaymentStandingUseCase;
    }

    @GetMapping("/program-enrollments/{id}/payment-standing")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR', 'PRECEPTOR')")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "ENROLLMENT_PAYMENT_STANDING_NOT_FOUND"))
    public EnrollmentPaymentStandingResponse getProgramEnrollmentStanding(@PathVariable Long id) {
        return getEnrollmentPaymentStandingUseCase.getStandingOfEnrollment(EnrollmentKind.PROGRAM, id);
    }

    @GetMapping("/course-enrollments/{id}/payment-standing")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR', 'PRECEPTOR')")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "ENROLLMENT_PAYMENT_STANDING_NOT_FOUND"))
    public EnrollmentPaymentStandingResponse getCourseEnrollmentStanding(@PathVariable Long id) {
        return getEnrollmentPaymentStandingUseCase.getStandingOfEnrollment(EnrollmentKind.COURSE, id);
    }

    @GetMapping("/program-enrollments/{id}/payment-standing/history")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "ENROLLMENT_PAYMENT_STANDING_NOT_FOUND"))
    public List<EnrollmentPaymentStatusChangeResponse> listProgramEnrollmentStandingHistory(@PathVariable Long id) {
        return getEnrollmentPaymentStandingUseCase.listStatusChangesOfEnrollment(EnrollmentKind.PROGRAM, id);
    }

    @GetMapping("/course-enrollments/{id}/payment-standing/history")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "ENROLLMENT_PAYMENT_STANDING_NOT_FOUND"))
    public List<EnrollmentPaymentStatusChangeResponse> listCourseEnrollmentStandingHistory(@PathVariable Long id) {
        return getEnrollmentPaymentStandingUseCase.listStatusChangesOfEnrollment(EnrollmentKind.COURSE, id);
    }
}
