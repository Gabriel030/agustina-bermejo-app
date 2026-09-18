package com.red_escuela.backend.billing.controller;

import com.red_escuela.backend.billing.dto.PaymentResponse;
import com.red_escuela.backend.billing.dto.RegisterPaymentRequest;
import com.red_escuela.backend.billing.usecase.payment.RegisterPaymentUseCase;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {
    private final RegisterPaymentUseCase registerPaymentUseCase;
    private final CurrentPersonService currentPersonService;

    public PaymentController(RegisterPaymentUseCase registerPaymentUseCase, CurrentPersonService currentPersonService) {
        this.registerPaymentUseCase = registerPaymentUseCase;
        this.currentPersonService = currentPersonService;
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('ALUMNO')")
    public PaymentResponse register(@Valid @RequestBody RegisterPaymentRequest request) {
        return PaymentResponse.from(registerPaymentUseCase.register(request, currentPersonService.resolveCurrentPerson()));
    }
}
