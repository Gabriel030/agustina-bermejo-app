package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.EnrollmentBillingStatusResponse;
import com.red_escuela.backend.dto.InvoiceResponse;
import com.red_escuela.backend.entity.Invoice;
import com.red_escuela.backend.service.CurrentStudentService;
import com.red_escuela.backend.service.InvoiceQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/me")
public class MeInvoiceController {

    private final InvoiceQueryService invoiceQueryService;
    private final CurrentStudentService currentStudentService;

    public MeInvoiceController(InvoiceQueryService invoiceQueryService, CurrentStudentService currentStudentService) {
        this.invoiceQueryService = invoiceQueryService;
        this.currentStudentService = currentStudentService;
    }

    @GetMapping("/course-enrollments/{id}/invoices")
    public EnrollmentBillingStatusResponse misCuotasDeCurso(@PathVariable Long id) {
        Long studentId = currentStudentService.resolveCurrentStudent().getId();
        return toStatusResponse(invoiceQueryService.porCursoPropio(studentId, id));
    }

    @GetMapping("/program-enrollments/{id}/invoices")
    public EnrollmentBillingStatusResponse misCuotasDeCarrera(@PathVariable Long id) {
        Long studentId = currentStudentService.resolveCurrentStudent().getId();
        return toStatusResponse(invoiceQueryService.porCarreraPropia(studentId, id));
    }

    private EnrollmentBillingStatusResponse toStatusResponse(List<Invoice> invoices) {
        return new EnrollmentBillingStatusResponse("UP_TO_DATE", LocalDateTime.now(),
                invoices.stream().map(InvoiceResponse::from).toList());
    }
}
