package com.red_escuela.backend.billing.usecase.paymentgateway;

import com.red_escuela.backend.billing.payment.service.MercadoPagoWebhookRegistration;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ProcessMpWebhookService implements ProcessMpWebhookUseCase {

    private final MercadoPagoWebhookRegistration webhookRegistration;

    public ProcessMpWebhookService(MercadoPagoWebhookRegistration webhookRegistration) {
        this.webhookRegistration = webhookRegistration;
    }

    @Override
    public void process(String xSignature, String xRequestId, String mpPaymentId) {
        webhookRegistration.register(xSignature, xRequestId, mpPaymentId);
    }
}
