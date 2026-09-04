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

    // Spec: "ADMINISTRATIVO|autoservicio alumno" sobre el mismo path — el chequeo de ownership
    // (esta matriculación es del alumno autenticado) vive en el service
    // (PaymentPlanSelectionService.validarPropiaSiEsAlumno), no acá: un Administrativo no tiene
    // fila Student propia, así que el mismo código sirve para ambos roles sin ramificar.
    @PostMapping("/course-enrollments/{id}/payment-plan")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('ALUMNO')")
    public PaymentPlanSelectionResponse seleccionarParaCurso(@PathVariable Long id, @Valid @RequestBody SelectPaymentPlanRequest request) {
        return PaymentPlanSelectionResponse.from(paymentPlanSelectionService.seleccionarParaCurso(id, request));
    }

    @PostMapping("/program-enrollments/{id}/payment-plan")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('ALUMNO')")
    public PaymentPlanSelectionResponse seleccionarParaCarrera(@PathVariable Long id, @Valid @RequestBody SelectPaymentPlanRequest request) {
        return PaymentPlanSelectionResponse.from(paymentPlanSelectionService.seleccionarParaCarrera(id, request));
    }
}
