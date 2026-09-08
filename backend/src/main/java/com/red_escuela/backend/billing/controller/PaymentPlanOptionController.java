package com.red_escuela.backend.billing.controller;

import com.red_escuela.backend.billing.dto.CreatePaymentPlanOptionRequest;
import com.red_escuela.backend.billing.dto.PaymentPlanOptionResponse;
import com.red_escuela.backend.billing.service.PaymentPlanOptionService;
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

    private final PaymentPlanOptionService paymentPlanOptionService;

    public PaymentPlanOptionController(PaymentPlanOptionService paymentPlanOptionService) {
        this.paymentPlanOptionService = paymentPlanOptionService;
    }

    // Defines cadence and the on-time-payment discount — it's a pricing rule, not an
    // Administrativo operation (2026-09-03 decision).
    @PostMapping("/courses/{id}/payment-plan-options")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public PaymentPlanOptionResponse crearParaCurso(@PathVariable Long id, @Valid @RequestBody CreatePaymentPlanOptionRequest request) {
        return PaymentPlanOptionResponse.from(paymentPlanOptionService.crearParaCurso(id, request));
    }

    @PostMapping("/programs/{id}/payment-plan-options")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public PaymentPlanOptionResponse crearParaCarrera(@PathVariable Long id, @Valid @RequestBody CreatePaymentPlanOptionRequest request) {
        return PaymentPlanOptionResponse.from(paymentPlanOptionService.crearParaCarrera(id, request));
    }
}
