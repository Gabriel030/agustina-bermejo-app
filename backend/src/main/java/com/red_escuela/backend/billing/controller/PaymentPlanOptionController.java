package com.red_escuela.backend.billing.controller;

import com.red_escuela.backend.billing.dto.CreatePaymentPlanOptionRequest;
import com.red_escuela.backend.billing.dto.PaymentPlanOptionResponse;
import com.red_escuela.backend.billing.usecase.paymentplan.CreatePaymentPlanOptionForCourseUseCase;
import com.red_escuela.backend.billing.usecase.paymentplan.CreatePaymentPlanOptionForProgramUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentPlanOptionController {
    private final CreatePaymentPlanOptionForCourseUseCase createForCourseUseCase;
    private final CreatePaymentPlanOptionForProgramUseCase createForProgramUseCase;

    public PaymentPlanOptionController(CreatePaymentPlanOptionForCourseUseCase createForCourseUseCase,
                                        CreatePaymentPlanOptionForProgramUseCase createForProgramUseCase) {
        this.createForCourseUseCase = createForCourseUseCase;
        this.createForProgramUseCase = createForProgramUseCase;
    }

    @PostMapping("/courses/{id}/payment-plan-options")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public PaymentPlanOptionResponse createForCourse(@PathVariable Long id, @Valid @RequestBody CreatePaymentPlanOptionRequest request) {
        return PaymentPlanOptionResponse.from(createForCourseUseCase.create(id, request));
    }

    @PostMapping("/programs/{id}/payment-plan-options")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public PaymentPlanOptionResponse createForProgram(@PathVariable Long id, @Valid @RequestBody CreatePaymentPlanOptionRequest request) {
        return PaymentPlanOptionResponse.from(createForProgramUseCase.create(id, request));
    }
}
