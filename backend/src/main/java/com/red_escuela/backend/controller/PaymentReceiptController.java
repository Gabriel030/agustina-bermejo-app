package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.PaymentReceiptResponse;
import com.red_escuela.backend.dto.RejectPaymentReceiptRequest;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.service.CurrentPersonService;
import com.red_escuela.backend.service.PaymentReceiptService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Comprobante de pago — el alta la dispara el propio Alumno (vía el flujo que
 * corresponda: Épica 7 Fase 3 para matrícula/primera cuota, Épica 8 Fase 2 para
 * cuotas regulares), la aprobación/rechazo es de Administración. Sin
 * @PreAuthorize en el alta a propósito: el ownership de la factura no se valida
 * acá todavía (ningún caller real existe en esta fase — se agrega la
 * verificación de "el Alumno solo sube comprobantes de sus propias facturas"
 * cuando se conecte el primer flujo real que llama a este endpoint).
 */
@RestController
public class PaymentReceiptController {

    private final PaymentReceiptService paymentReceiptService;
    private final CurrentPersonService currentPersonService;

    public PaymentReceiptController(PaymentReceiptService paymentReceiptService, CurrentPersonService currentPersonService) {
        this.paymentReceiptService = paymentReceiptService;
        this.currentPersonService = currentPersonService;
    }

    @PostMapping("/invoices/{id}/payment-receipts")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentReceiptResponse upload(@PathVariable Long id, @RequestPart MultipartFile file) {
        return PaymentReceiptResponse.from(paymentReceiptService.upload(id, file));
    }

    @GetMapping("/invoices/{id}/payment-receipts")
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<PaymentReceiptResponse> listByInvoice(@PathVariable Long id) {
        return paymentReceiptService.listByInvoice(id).stream().map(PaymentReceiptResponse::from).toList();
    }

    @GetMapping("/payment-receipts")
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<PaymentReceiptResponse> listPending() {
        return paymentReceiptService.listPending().stream().map(PaymentReceiptResponse::from).toList();
    }

    @PostMapping("/payment-receipts/{id}/approve")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public PaymentReceiptResponse approve(@PathVariable Long id) {
        return PaymentReceiptResponse.from(paymentReceiptService.approve(id, actor()));
    }

    @PostMapping("/payment-receipts/{id}/reject")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public PaymentReceiptResponse reject(@PathVariable Long id, @Valid @RequestBody RejectPaymentReceiptRequest request) {
        return PaymentReceiptResponse.from(paymentReceiptService.reject(id, actor(), request.reason()));
    }

    private Person actor() {
        return currentPersonService.resolveCurrentPerson();
    }
}
