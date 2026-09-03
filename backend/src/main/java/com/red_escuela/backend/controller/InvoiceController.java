package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.CreateInvoiceFromFeeScheduleRequest;
import com.red_escuela.backend.dto.CreateInvoiceRequest;
import com.red_escuela.backend.dto.InvoiceResponse;
import com.red_escuela.backend.dto.PaymentResponse;
import com.red_escuela.backend.dto.PaymentStandingResponse;
import com.red_escuela.backend.dto.RegistrarPagoRequest;
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
    public InvoiceResponse crear(@PathVariable Long id, @Valid @RequestBody CreateInvoiceRequest request) {
        var invoice = invoiceService.crear(id, request);
        return InvoiceResponse.from(invoice, invoice.getAmount());
    }

    // Resuelve el precio vigente (matrícula o cuota) más descuentos/promociones
    // aplicables, en vez del monto libre de `crear` — pensado para el flujo de
    // Épica 7 Fase 3 (matrícula/primera cuota al inscribirse) y Épica 8 Fase 2
    // (cuotas regulares), no reemplaza el alta manual existente.
    @PostMapping("/students/{id}/invoices/from-fee-schedule")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public InvoiceResponse crearDesdeFeeSchedule(@PathVariable Long id, @Valid @RequestBody CreateInvoiceFromFeeScheduleRequest request) {
        var invoice = invoiceService.crearDesdeFeeSchedule(id, request);
        return InvoiceResponse.from(invoice, invoice.getAmount());
    }

    @GetMapping("/students/{id}/invoices")
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<InvoiceResponse> listar(@PathVariable Long id) {
        return invoiceService.listarPorAlumno(id);
    }

    @PostMapping("/invoices/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public PaymentResponse registrarPago(@PathVariable Long id, @Valid @RequestBody RegistrarPagoRequest request) {
        return PaymentResponse.from(paymentService.registrarPago(id, request));
    }

    @PostMapping("/invoices/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public void cancelar(@PathVariable Long id) {
        invoiceService.cancelar(id);
    }

    // Decisión 17, consulta — coherente con decisión 14: el Preceptor no tiene acceso a facturación.
    @GetMapping("/students/{id}/payment-standing")
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public PaymentStandingResponse verEstadoDePago(@PathVariable Long id) {
        return new PaymentStandingResponse(id, invoiceService.calcularEstadoDePago(id).name());
    }
}
