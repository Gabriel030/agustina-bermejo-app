package com.red_escuela.backend.billing.payment.controller;

import com.red_escuela.backend.billing.payment.dto.PaymentReversalPreviewResponse;
import com.red_escuela.backend.billing.payment.dto.RegisterChargebackRequest;
import com.red_escuela.backend.billing.payment.dto.RevokeBankTransferApprovalRequest;
import com.red_escuela.backend.billing.payment.usecase.PreviewPaymentReversalUseCase;
import com.red_escuela.backend.shared.module.RequiresModule;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiresModule(RequiresModule.BILLING)
public class PaymentReversalPreviewController {
    private final PreviewPaymentReversalUseCase previewPaymentReversalUseCase;

    public PaymentReversalPreviewController(PreviewPaymentReversalUseCase previewPaymentReversalUseCase) {
        this.previewPaymentReversalUseCase = previewPaymentReversalUseCase;
    }

    @PostMapping("/payments/{paymentId}/chargebacks/preview")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Impacto del contracargo sin registrarlo; si hay bloqueo, allowed=false y blockedBy con el código de la regla. Los efectos sobre facturas y recargos todavía no se calculan (effects.calculated=false)"),
            @ApiResponse(responseCode = "404", description = "PAYMENT_NOT_FOUND")})
    public PaymentReversalPreviewResponse previewChargebackRegistration(@PathVariable Long paymentId, @Valid @RequestBody RegisterChargebackRequest request) {
        return PaymentReversalPreviewResponse.from(previewPaymentReversalUseCase.previewChargebackRegistration(paymentId, request));
    }

    @PostMapping("/chargebacks/{chargebackId}/reversal/preview")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Impacto de la reversión del contracargo sin aplicarla; si hay bloqueo, allowed=false y blockedBy con el código de la regla"),
            @ApiResponse(responseCode = "404", description = "CHARGEBACK_NOT_FOUND")})
    public PaymentReversalPreviewResponse previewChargebackReversal(@PathVariable Long chargebackId) {
        return PaymentReversalPreviewResponse.from(previewPaymentReversalUseCase.previewChargebackReversal(chargebackId));
    }

    @PostMapping("/payments/{paymentId}/approval-revocation/preview")
    @PreAuthorize("hasRole('DIRECTOR')")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Impacto de la revocación sin aplicarla; si hay bloqueo, allowed=false y blockedBy con el código de la regla"),
            @ApiResponse(responseCode = "404", description = "PAYMENT_NOT_FOUND")})
    public PaymentReversalPreviewResponse previewApprovalRevocation(@PathVariable Long paymentId, @Valid @RequestBody RevokeBankTransferApprovalRequest request) {
        return PaymentReversalPreviewResponse.from(previewPaymentReversalUseCase.previewApprovalRevocation(paymentId, request));
    }
}
