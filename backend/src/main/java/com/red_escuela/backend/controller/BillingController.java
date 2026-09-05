package com.red_escuela.backend.controller;

import com.red_escuela.backend.service.RecurringBillingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Disparo manual del motor de facturación recurrente (Épica 8 Fase 2) — mismo
 * método que llama el cron, sin lógica duplicada. Existe para el caso real de
 * "el backend estuvo caído la noche del corte mensual": Administración no
 * necesita esperar a la corrida de mañana ni tocar la base a mano, dispara la
 * corrida de recupero ahora. Seguro de llamar las veces que haga falta — hereda
 * la idempotencia de RecurringBillingService (constraint único), correrlo "por
 * las dudas" nunca duplica una factura ya generada.
 */
@RestController
public class BillingController {

    private final RecurringBillingService recurringBillingService;

    public BillingController(RecurringBillingService recurringBillingService) {
        this.recurringBillingService = recurringBillingService;
    }

    @PostMapping("/billing/run")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public void runManually() {
        recurringBillingService.generateDailyInstallments();
    }
}
