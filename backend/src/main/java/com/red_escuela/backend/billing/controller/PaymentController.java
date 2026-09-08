package com.red_escuela.backend.billing.controller;

import com.red_escuela.backend.billing.dto.PaymentResponse;
import com.red_escuela.backend.billing.dto.RegisterPaymentRequest;
import com.red_escuela.backend.service.CurrentPersonService;
import com.red_escuela.backend.billing.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.red_escuela.backend.billing.service.PaymentService;

@RestController
public class PaymentController {

    private final PaymentService paymentService;
    private final CurrentPersonService currentPersonService;

    public PaymentController(PaymentService paymentService, CurrentPersonService currentPersonService) {
        this.paymentService = paymentService;
        this.currentPersonService = currentPersonService;
    }

    // Spec: "ADMINISTRATIVO|autoservicio alumno (solo sobre invoices propias)" — el chequeo de
    // ownership vive en PaymentService (mismo criterio que PaymentPlanSelectionService: un
    // Administrativo no tiene fila Student propia, así que el mismo código sirve para ambos
    // roles sin ramificar acá).
    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('ALUMNO')")
    public PaymentResponse register(@Valid @RequestBody RegisterPaymentRequest request) {
        return PaymentResponse.from(paymentService.registerPayment(request, currentPersonService.resolveCurrentPerson()));
    }
}
