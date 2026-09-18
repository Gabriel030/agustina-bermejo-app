package com.red_escuela.backend.billing.controller;

import com.red_escuela.backend.billing.dto.CreateInvoiceAdjustmentRequest;
import com.red_escuela.backend.billing.dto.EnrollmentBillingStatusResponse;
import com.red_escuela.backend.billing.dto.InvoiceResponse;
import com.red_escuela.backend.billing.dto.PriceBreakdownResponse;
import com.red_escuela.backend.enrollment.entity.BillableEnrollment;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.service.InvoiceQueryService;
import com.red_escuela.backend.billing.usecase.invoice.ApplyInvoiceAdjustmentCommand;
import com.red_escuela.backend.billing.usecase.invoice.ApplyInvoiceAdjustmentUseCase;
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
    private final ApplyInvoiceAdjustmentUseCase applyInvoiceAdjustmentUseCase;

    public InvoiceController(InvoiceQueryService invoiceQueryService, ApplyInvoiceAdjustmentUseCase applyInvoiceAdjustmentUseCase) {
        this.invoiceQueryService = invoiceQueryService;
        this.applyInvoiceAdjustmentUseCase = applyInvoiceAdjustmentUseCase;
    }

    @GetMapping("/course-enrollments/{id}/invoices")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public EnrollmentBillingStatusResponse byCourse(@PathVariable Long id) {
        return toStatusResponse(invoiceQueryService.courseBillable(id), invoiceQueryService.byCourse(id));
    }

    @GetMapping("/program-enrollments/{id}/invoices")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public EnrollmentBillingStatusResponse byProgram(@PathVariable Long id) {
        return toStatusResponse(invoiceQueryService.programBillable(id), invoiceQueryService.byProgram(id));
    }

    @PostMapping("/invoices/{id}/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public InvoiceResponse applyAdjustment(@PathVariable Long id, @Valid @RequestBody CreateInvoiceAdjustmentRequest request) {
        return InvoiceResponse.from(applyInvoiceAdjustmentUseCase.apply(new ApplyInvoiceAdjustmentCommand(id, request)));
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
