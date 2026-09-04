package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.CreateInvoiceAdjustmentRequest;
import com.red_escuela.backend.dto.EnrollmentBillingStatusResponse;
import com.red_escuela.backend.dto.InvoiceResponse;
import com.red_escuela.backend.dto.PriceBreakdownResponse;
import com.red_escuela.backend.service.InvoiceAdjustmentService;
import com.red_escuela.backend.service.InvoiceQueryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

// billingStanding is fixed "UP_TO_DATE" until Fase 6.3 defines the real state machine (ver
// EnrollmentBillingStatusResponse).
@RestController
public class InvoiceController {

    private final InvoiceQueryService invoiceQueryService;
    private final InvoiceAdjustmentService invoiceAdjustmentService;

    public InvoiceController(InvoiceQueryService invoiceQueryService, InvoiceAdjustmentService invoiceAdjustmentService) {
        this.invoiceQueryService = invoiceQueryService;
        this.invoiceAdjustmentService = invoiceAdjustmentService;
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

    // Decisión 2026-09-03: solo el Director otorga becas/descuentos/condonaciones —
    // Administrativo ya no puede cargar un DISCOUNT/WAIVE_DEBT directo.
    @PostMapping("/invoices/{id}/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public InvoiceResponse applyAdjustment(@PathVariable Long id, @Valid @RequestBody CreateInvoiceAdjustmentRequest request) {
        return InvoiceResponse.from(invoiceAdjustmentService.applyAdjustment(id, request));
    }

    @GetMapping("/invoices/{id}/price-breakdown")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public PriceBreakdownResponse priceBreakdown(@PathVariable Long id) {
        return invoiceQueryService.priceBreakdown(id);
    }

    private EnrollmentBillingStatusResponse toStatusResponse(java.util.List<com.red_escuela.backend.entity.Invoice> invoices) {
        return new EnrollmentBillingStatusResponse("UP_TO_DATE", LocalDateTime.now(),
                invoices.stream().map(InvoiceResponse::from).toList());
    }
}
