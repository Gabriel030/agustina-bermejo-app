package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.PaymentReceiptResponse;
import com.red_escuela.backend.dto.RejectPaymentReceiptRequest;
import com.red_escuela.backend.service.CurrentPersonService;
import com.red_escuela.backend.service.PaymentReceiptService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/payment-receipts")
public class PaymentReceiptController {

    private final PaymentReceiptService paymentReceiptService;
    private final CurrentPersonService currentPersonService;

    public PaymentReceiptController(PaymentReceiptService paymentReceiptService, CurrentPersonService currentPersonService) {
        this.paymentReceiptService = paymentReceiptService;
        this.currentPersonService = currentPersonService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<PaymentReceiptResponse> listarPendientes() {
        return paymentReceiptService.listarPendientes().stream().map(PaymentReceiptResponse::from).toList();
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public PaymentReceiptResponse aprobar(@PathVariable Long id) {
        return PaymentReceiptResponse.from(paymentReceiptService.aprobar(id, currentPersonService.resolveCurrentPerson()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public PaymentReceiptResponse reject(@PathVariable Long id, @Valid @RequestBody RejectPaymentReceiptRequest request) {
        return PaymentReceiptResponse.from(
                paymentReceiptService.reject(id, request.rejectionReason(), currentPersonService.resolveCurrentPerson()));
    }
}
