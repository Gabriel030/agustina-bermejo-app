package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.EnrollmentBillingStatusResponse;
import com.red_escuela.backend.dto.InvoiceResponse;
import com.red_escuela.backend.service.InvoiceQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

// Read-only in this phase — paying is Fase 6.2. billingStanding is fixed "UP_TO_DATE" until
// Fase 6.3 defines the real state machine (ver EnrollmentBillingStatusResponse).
@RestController
public class InvoiceController {

    private final InvoiceQueryService invoiceQueryService;

    public InvoiceController(InvoiceQueryService invoiceQueryService) {
        this.invoiceQueryService = invoiceQueryService;
    }

    @GetMapping("/course-enrollments/{id}/invoices")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public EnrollmentBillingStatusResponse porCurso(@PathVariable Long id) {
        return toStatusResponse(invoiceQueryService.porCurso(id));
    }

    @GetMapping("/program-enrollments/{id}/invoices")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public EnrollmentBillingStatusResponse porCarrera(@PathVariable Long id) {
        return toStatusResponse(invoiceQueryService.porCarrera(id));
    }

    private EnrollmentBillingStatusResponse toStatusResponse(java.util.List<com.red_escuela.backend.entity.Invoice> invoices) {
        return new EnrollmentBillingStatusResponse("UP_TO_DATE", LocalDateTime.now(),
                invoices.stream().map(InvoiceResponse::from).toList());
    }
}
