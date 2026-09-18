package com.red_escuela.backend.billing.usecase.paymentgateway;

public interface ProcessMpWebhookUseCase {

    void process(String xSignature, String xRequestId, String mpPaymentId);
}
