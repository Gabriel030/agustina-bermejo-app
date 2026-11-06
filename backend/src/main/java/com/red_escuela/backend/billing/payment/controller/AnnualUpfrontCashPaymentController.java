package com.red_escuela.backend.billing.payment.controller;

import com.red_escuela.backend.billing.payment.domain.AnnualUpfrontCashPaymentResult;
import com.red_escuela.backend.billing.payment.dto.RegisterAnnualUpfrontCashPaymentRequest;
import com.red_escuela.backend.billing.payment.usecase.RegisterAnnualUpfrontCashPaymentUseCase;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.RequiresModule;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiresModule(RequiresModule.BILLING)
public class AnnualUpfrontCashPaymentController {
    private final RegisterAnnualUpfrontCashPaymentUseCase registerCashPayment;

    public AnnualUpfrontCashPaymentController(RegisterAnnualUpfrontCashPaymentUseCase registerCashPayment) {
        this.registerCashPayment = registerCashPayment;
    }

    @RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.FULL)
    @PostMapping("/students/{studentId}/annual-upfront-requests/{requestId}/cash-payment")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "IDEMPOTENCY_KEY_INVALID (obligatoria, hasta 100 caracteres), ANNUAL_UPFRONT_RECEIPT_AMOUNT_MUST_BE_TOTAL (el cobro no es por el total) o ANNUAL_UPFRONT_RECEIPT_LATE (el cobro en efectivo se registró después del plazo)"),
            @ApiResponse(responseCode = "404", description = "ANNUAL_UPFRONT_REQUEST_NOT_FOUND: el pedido no existe o no es del alumno"),
            @ApiResponse(responseCode = "409", description = "ANNUAL_UPFRONT_PAYMENT_EXPIRED (el pedido ya fue descartado), ANNUAL_UPFRONT_PAYMENT_ALREADY_INITIATED o IDEMPOTENCY_KEY_CONFLICT")})
    public AnnualUpfrontCashPaymentResult registerAnnualUpfrontCashPayment(@PathVariable Long studentId, @PathVariable Long requestId,
                                                                           @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true) @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                                           @Valid @RequestBody RegisterAnnualUpfrontCashPaymentRequest request) {
        return registerCashPayment.registerAnnualUpfrontCashPayment(studentId, requestId, request, idempotencyKey);
    }
}
