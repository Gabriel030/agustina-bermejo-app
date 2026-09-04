package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.CreateInvoiceAdjustmentRequest;
import com.red_escuela.backend.dto.EnrollmentBillingStatusResponse;
import com.red_escuela.backend.dto.InvoiceResponse;
import com.red_escuela.backend.dto.PriceBreakdownResponse;
import com.red_escuela.backend.entity.BillableEnrollment;
import com.red_escuela.backend.entity.Invoice;
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

import java.util.List;

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
        return toStatusResponse(invoiceQueryService.courseBillable(id), invoiceQueryService.porCurso(id));
    }

    @GetMapping("/program-enrollments/{id}/invoices")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public EnrollmentBillingStatusResponse porCarrera(@PathVariable Long id) {
        return toStatusResponse(invoiceQueryService.programBillable(id), invoiceQueryService.porCarrera(id));
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

    private EnrollmentBillingStatusResponse toStatusResponse(BillableEnrollment billable, List<Invoice> invoices) {
        return new EnrollmentBillingStatusResponse(billable.getBillingStanding().name(), billable.getBillingStandingUpdatedAt(),
                invoices.stream().map(InvoiceResponse::from).toList());
    }
}
