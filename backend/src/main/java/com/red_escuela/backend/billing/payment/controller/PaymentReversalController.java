package com.red_escuela.backend.billing.payment.controller;

import com.red_escuela.backend.billing.payment.dto.PaymentReversalResponse;
import com.red_escuela.backend.billing.payment.dto.RegisterChargebackRequest;
import com.red_escuela.backend.billing.payment.dto.RevokeBankTransferApprovalRequest;
import com.red_escuela.backend.billing.payment.usecase.RegisterChargebackUseCase;
import com.red_escuela.backend.billing.payment.usecase.ReverseChargebackUseCase;
import com.red_escuela.backend.billing.payment.usecase.RevokeBankTransferApprovalUseCase;
import com.red_escuela.backend.shared.module.RequiresModule;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiresModule(RequiresModule.BILLING)
public class PaymentReversalController {
    private final RevokeBankTransferApprovalUseCase revokeBankTransferApprovalUseCase;
    private final RegisterChargebackUseCase registerChargebackUseCase;
    private final ReverseChargebackUseCase reverseChargebackUseCase;

    public PaymentReversalController(RevokeBankTransferApprovalUseCase revokeBankTransferApprovalUseCase, RegisterChargebackUseCase registerChargebackUseCase,
                                     ReverseChargebackUseCase reverseChargebackUseCase) {
        this.revokeBankTransferApprovalUseCase = revokeBankTransferApprovalUseCase;
        this.registerChargebackUseCase = registerChargebackUseCase;
        this.reverseChargebackUseCase = reverseChargebackUseCase;
    }

    @PostMapping("/payments/{paymentId}/approval-revocation")
    @PreAuthorize("hasRole('DIRECTOR')")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "PAYMENT_REJECTION_REASON_REQUIRED, PAYMENT_REJECTION_REASON_TOO_LONG"),
            @ApiResponse(responseCode = "404", description = "PAYMENT_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "ILLEGAL_PAYMENT_STATUS_TRANSITION (el pago no es una transferencia manual aprobada), PAYMENT_ALREADY_REFUNDED (D-262) o INSUFFICIENT_ACCOUNT_CREDIT")})
    public PaymentReversalResponse revokeBankTransferApproval(@PathVariable Long paymentId, @Valid @RequestBody RevokeBankTransferApprovalRequest request) {
        return PaymentReversalResponse.from(revokeBankTransferApprovalUseCase.revokeBankTransferApproval(paymentId, request.reason()));
    }

    @PostMapping("/payments/{paymentId}/chargebacks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "CHARGEBACK_REFERENCE_INVALID, CHARGEBACK_DATE_INVALID"),
            @ApiResponse(responseCode = "404", description = "PAYMENT_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "ILLEGAL_PAYMENT_STATUS_TRANSITION (el pago no está aprobado), CHARGEBACK_MUST_BE_FOR_THE_FULL_AMOUNT y PAYMENT_ALREADY_REFUNDED (D-262) o INSUFFICIENT_ACCOUNT_CREDIT")})
    public PaymentReversalResponse registerChargeback(@PathVariable Long paymentId, @Valid @RequestBody RegisterChargebackRequest request) {
        return PaymentReversalResponse.from(registerChargebackUseCase.registerChargeback(paymentId, request));
    }

    @PostMapping("/chargebacks/{chargebackId}/reversal")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "CHARGEBACK_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "CHARGEBACK_ALREADY_REVERSED o ILLEGAL_PAYMENT_STATUS_TRANSITION")})
    public PaymentReversalResponse reverseChargeback(@PathVariable Long chargebackId) {
        return PaymentReversalResponse.from(reverseChargebackUseCase.reverseChargeback(chargebackId));
    }
}
