package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.DebtSummaryResponse;
import com.red_escuela.backend.dto.InvoiceResponse;
import com.red_escuela.backend.dto.PaymentStandingResponse;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.service.CurrentStudentService;
import com.red_escuela.backend.service.InvoiceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Autoservicio del alumno (RF-AL-09, decisión 14): el Student siempre se resuelve del JWT vía
 * CurrentStudentService, nunca de un id de la URL — mismo criterio que MeEnrollmentController.
 */
@RestController
@RequestMapping("/me")
public class MeInvoiceController {

    private final CurrentStudentService currentStudentService;
    private final InvoiceService invoiceService;

    public MeInvoiceController(CurrentStudentService currentStudentService, InvoiceService invoiceService) {
        this.currentStudentService = currentStudentService;
        this.invoiceService = invoiceService;
    }

    @GetMapping("/invoices")
    public List<InvoiceResponse> misFacturas() {
        Student student = currentStudentService.resolveCurrentStudent();
        return invoiceService.listByStudent(student.getId());
    }

    @GetMapping("/invoices/debt-summary")
    public DebtSummaryResponse miResumenDeDeuda() {
        Student student = currentStudentService.resolveCurrentStudent();
        return invoiceService.calculateDebt(student.getId());
    }

    @GetMapping("/payment-standing")
    public PaymentStandingResponse miEstadoDePago() {
        Student student = currentStudentService.resolveCurrentStudent();
        return new PaymentStandingResponse(student.getId(), invoiceService.calculatePaymentStatus(student.getId()).name());
    }
}
