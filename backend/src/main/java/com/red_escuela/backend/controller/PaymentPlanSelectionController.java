package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.PaymentPlanSelectionResponse;
import com.red_escuela.backend.dto.SelectPaymentPlanRequest;
import com.red_escuela.backend.service.PaymentPlanSelectionService;
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

    private final PaymentPlanSelectionService paymentPlanSelectionService;

    public PaymentPlanSelectionController(PaymentPlanSelectionService paymentPlanSelectionService) {
        this.paymentPlanSelectionService = paymentPlanSelectionService;
    }

    // ADMINISTRATIVO-only for now: the spec calls for "ADMINISTRATIVO|autoservicio alumno" on
    // this same path, but that needs an ownership check (this enrollment belongs to the
    // authenticated student) that isn't implemented yet — opening it to ALUMNO without it would
    // let any student act on any other student's enrollment. Left as a follow-up before this
    // phase closes.
    @PostMapping("/course-enrollments/{id}/payment-plan")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public PaymentPlanSelectionResponse seleccionarParaCurso(@PathVariable Long id, @Valid @RequestBody SelectPaymentPlanRequest request) {
        return PaymentPlanSelectionResponse.from(paymentPlanSelectionService.seleccionarParaCurso(id, request));
    }

    @PostMapping("/program-enrollments/{id}/payment-plan")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public PaymentPlanSelectionResponse seleccionarParaCarrera(@PathVariable Long id, @Valid @RequestBody SelectPaymentPlanRequest request) {
        return PaymentPlanSelectionResponse.from(paymentPlanSelectionService.seleccionarParaCarrera(id, request));
    }
}
