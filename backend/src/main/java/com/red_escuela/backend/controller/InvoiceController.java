package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.CreateInvoiceFromFeeScheduleRequest;
import com.red_escuela.backend.dto.CreateInvoiceRequest;
import com.red_escuela.backend.dto.InvoiceResponse;
import com.red_escuela.backend.dto.PaymentResponse;
import com.red_escuela.backend.dto.PaymentStandingResponse;
import com.red_escuela.backend.dto.RegisterPaymentRequest;
import com.red_escuela.backend.service.InvoiceService;
import com.red_escuela.backend.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** RF-AL-09/RF-AD-08/RF-AL-10: facturación y registro de pagos, exclusivo de secretaría (decisión 14). */
@RestController
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final PaymentService paymentService;

    public InvoiceController(InvoiceService invoiceService, PaymentService paymentService) {
        this.invoiceService = invoiceService;
        this.paymentService = paymentService;
    }

    @PostMapping("/students/{id}/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public InvoiceResponse create(@PathVariable Long id, @Valid @RequestBody CreateInvoiceRequest request) {
        var invoice = invoiceService.create(id, request);
        return InvoiceResponse.from(invoice, invoice.getAmount());
    }

    // Resuelve el precio vigente (matrícula o cuota) más descuentos/promociones
    // aplicables, en vez del monto libre de `create` — pensado para el flujo de
    // Épica 7 Fase 3 (matrícula/primera cuota al inscribirse) y Épica 8 Fase 2
    // (cuotas regulares), no reemplaza el alta manual existente.
    @PostMapping("/students/{id}/invoices/from-fee-schedule")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public InvoiceResponse createFromFeeSchedule(@PathVariable Long id, @Valid @RequestBody CreateInvoiceFromFeeScheduleRequest request) {
        var invoice = invoiceService.createFromFeeSchedule(id, request);
        return InvoiceResponse.from(invoice, invoice.getAmount());
    }

    @GetMapping("/students/{id}/invoices")
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<InvoiceResponse> list(@PathVariable Long id) {
        return invoiceService.listByStudent(id);
    }

    @PostMapping("/invoices/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public PaymentResponse registerPayment(@PathVariable Long id, @Valid @RequestBody RegisterPaymentRequest request) {
        return PaymentResponse.from(paymentService.registerPayment(id, request));
    }

    @PostMapping("/invoices/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public void cancel(@PathVariable Long id) {
        invoiceService.cancel(id);
    }

    // Decisión 17, consulta — coherente con decisión 14: el Preceptor no tiene acceso a facturación.
    @GetMapping("/students/{id}/payment-standing")
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public PaymentStandingResponse viewPaymentStatus(@PathVariable Long id) {
        return new PaymentStandingResponse(id, invoiceService.calculatePaymentStatus(id).name());
    }
}
