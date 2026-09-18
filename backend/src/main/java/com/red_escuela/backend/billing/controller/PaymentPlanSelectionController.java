package com.red_escuela.backend.billing.controller;

import com.red_escuela.backend.billing.dto.PaymentPlanSelectionResponse;
import com.red_escuela.backend.billing.dto.SelectPaymentPlanRequest;
import com.red_escuela.backend.billing.usecase.paymentplan.SelectPaymentPlanForCourseUseCase;
import com.red_escuela.backend.billing.usecase.paymentplan.SelectPaymentPlanForProgramUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentPlanSelectionController {
    private final SelectPaymentPlanForCourseUseCase selectForCourseUseCase;
    private final SelectPaymentPlanForProgramUseCase selectForProgramUseCase;

    public PaymentPlanSelectionController(SelectPaymentPlanForCourseUseCase selectForCourseUseCase,
                                           SelectPaymentPlanForProgramUseCase selectForProgramUseCase) {
        this.selectForCourseUseCase = selectForCourseUseCase;
        this.selectForProgramUseCase = selectForProgramUseCase;
    }

    @PostMapping("/course-enrollments/{id}/payment-plan")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('ALUMNO')")
    public PaymentPlanSelectionResponse selectForCourse(@PathVariable Long id, @Valid @RequestBody SelectPaymentPlanRequest request) {
        return PaymentPlanSelectionResponse.from(selectForCourseUseCase.select(id, request));
    }

    @PostMapping("/program-enrollments/{id}/payment-plan")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('ALUMNO')")
    public PaymentPlanSelectionResponse selectForProgram(@PathVariable Long id, @Valid @RequestBody SelectPaymentPlanRequest request) {
        return PaymentPlanSelectionResponse.from(selectForProgramUseCase.select(id, request));
    }
}
