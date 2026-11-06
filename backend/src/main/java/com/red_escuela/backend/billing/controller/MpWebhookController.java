package com.red_escuela.backend.billing.controller;

import com.red_escuela.backend.billing.payment.usecase.RecordMpWebhookEventUseCase;
import com.red_escuela.backend.billing.usecase.paymentgateway.ProcessMpWebhookUseCase;
import com.red_escuela.backend.shared.module.RequiresModule;
import com.red_escuela.backend.shared.module.BillingMode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Pattern;

@RestController
@RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.OFF)
public class MpWebhookController {

    private static final Pattern NUMERIC_ID = Pattern.compile("^\\d{1,20}$");

    private final ProcessMpWebhookUseCase processMpWebhookUseCase;

    private final RecordMpWebhookEventUseCase recordMpWebhookEventUseCase;

    public MpWebhookController(ProcessMpWebhookUseCase processMpWebhookUseCase, RecordMpWebhookEventUseCase recordMpWebhookEventUseCase) {
        this.processMpWebhookUseCase = processMpWebhookUseCase;
        this.recordMpWebhookEventUseCase = recordMpWebhookEventUseCase;
    }

    @PostMapping("/payments/mp-webhook")
    public void webhook(
            @RequestHeader(value = "x-signature", required = false, defaultValue = "") String xSignature,
            @RequestHeader(value = "x-request-id", required = false, defaultValue = "") String xRequestId,
            @RequestParam(value = "data.id") String mpPaymentId) {
        if (!NUMERIC_ID.matcher(mpPaymentId).matches()) {
            throw new IllegalArgumentException("Identificador de pago inválido");
        }
        if (recordMpWebhookEventUseCase.recordWhenBillingDoesNotRegisterPayments(xSignature, xRequestId, mpPaymentId)) {
            return;
        }
        processMpWebhookUseCase.process(xSignature, xRequestId, mpPaymentId);
    }
}
