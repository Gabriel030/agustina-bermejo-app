package com.red_escuela.backend.billing.payment.usecase;

public interface RecordMpWebhookEventUseCase {

    boolean recordWhenBillingDoesNotRegisterPayments(String xSignature, String xRequestId, String mpPaymentId);
}
