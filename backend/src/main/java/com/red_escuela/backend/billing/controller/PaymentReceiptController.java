package com.red_escuela.backend.billing.controller;

import com.red_escuela.backend.billing.dto.PaymentReceiptResponse;
import com.red_escuela.backend.billing.dto.RejectPaymentReceiptRequest;
import com.red_escuela.backend.billing.usecase.paymentreceipt.ApprovePaymentReceiptUseCase;
import com.red_escuela.backend.billing.usecase.paymentreceipt.ListPendingPaymentReceiptsUseCase;
import com.red_escuela.backend.billing.usecase.paymentreceipt.RejectPaymentReceiptUseCase;
import com.red_escuela.backend.identity.service.CurrentPersonService;
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

    private final ListPendingPaymentReceiptsUseCase listPendingPaymentReceiptsUseCase;
    private final ApprovePaymentReceiptUseCase approvePaymentReceiptUseCase;
    private final RejectPaymentReceiptUseCase rejectPaymentReceiptUseCase;
    private final CurrentPersonService currentPersonService;

    public PaymentReceiptController(ListPendingPaymentReceiptsUseCase listPendingPaymentReceiptsUseCase,
                                     ApprovePaymentReceiptUseCase approvePaymentReceiptUseCase, RejectPaymentReceiptUseCase rejectPaymentReceiptUseCase,
                                     CurrentPersonService currentPersonService) {
        this.listPendingPaymentReceiptsUseCase = listPendingPaymentReceiptsUseCase;
        this.approvePaymentReceiptUseCase = approvePaymentReceiptUseCase;
        this.rejectPaymentReceiptUseCase = rejectPaymentReceiptUseCase;
        this.currentPersonService = currentPersonService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<PaymentReceiptResponse> listPending() {
        return listPendingPaymentReceiptsUseCase.list().stream().map(PaymentReceiptResponse::from).toList();
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public PaymentReceiptResponse approve(@PathVariable Long id) {
        return PaymentReceiptResponse.from(approvePaymentReceiptUseCase.approve(id, currentPersonService.resolveCurrentPerson()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public PaymentReceiptResponse reject(@PathVariable Long id, @Valid @RequestBody RejectPaymentReceiptRequest request) {
        return PaymentReceiptResponse.from(
                rejectPaymentReceiptUseCase.reject(id, request.rejectionReason(), currentPersonService.resolveCurrentPerson()));
    }
}
