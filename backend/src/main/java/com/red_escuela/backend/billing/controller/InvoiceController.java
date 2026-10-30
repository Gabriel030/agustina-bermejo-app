package com.red_escuela.backend.billing.controller;

import com.red_escuela.backend.billing.dto.EnrollmentBillingStatusResponse;
import com.red_escuela.backend.billing.dto.InvoiceResponse;
import com.red_escuela.backend.billing.dto.PriceBreakdownResponse;
import com.red_escuela.backend.billing.service.InvoiceQueryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class InvoiceController {
    private final InvoiceQueryService invoiceQueryService;
    
    public InvoiceController(InvoiceQueryService invoiceQueryService) {
        this.invoiceQueryService = invoiceQueryService;
    }

    @GetMapping("/course-enrollments/{id}/invoices")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public EnrollmentBillingStatusResponse byCourse(@PathVariable Long id) {
        return EnrollmentBillingStatusResponse.of(invoiceQueryService.courseBillable(id), invoiceQueryService.courseStanding(id), invoiceQueryService.byCourse(id));
    }

    @GetMapping("/program-enrollments/{id}/invoices")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public EnrollmentBillingStatusResponse byProgram(@PathVariable Long id) {
        return EnrollmentBillingStatusResponse.of(invoiceQueryService.programBillable(id), invoiceQueryService.programStanding(id), invoiceQueryService.byProgram(id));
    }

    @GetMapping("/invoices/{id}/price-breakdown")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public PriceBreakdownResponse priceBreakdown(@PathVariable Long id) {
        return invoiceQueryService.priceBreakdown(id);
    }

}
