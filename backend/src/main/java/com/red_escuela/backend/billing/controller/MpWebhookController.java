package com.red_escuela.backend.billing.controller;

import com.red_escuela.backend.billing.usecase.paymentgateway.ProcessMpWebhookUseCase;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MpWebhookController {

    private final ProcessMpWebhookUseCase processMpWebhookUseCase;

    public MpWebhookController(ProcessMpWebhookUseCase processMpWebhookUseCase) {
        this.processMpWebhookUseCase = processMpWebhookUseCase;
    }

    @PostMapping("/payments/mp-webhook")
    public void webhook(
            @RequestHeader(value = "x-signature", required = false, defaultValue = "") String xSignature,
            @RequestHeader(value = "x-request-id", required = false, defaultValue = "") String xRequestId,
            @RequestParam(value = "data.id") String mpPaymentId) {
        processMpWebhookUseCase.process(xSignature, xRequestId, mpPaymentId);
    }
}
