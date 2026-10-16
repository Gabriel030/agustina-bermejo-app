package com.red_escuela.backend.billing.usecase.paymentgateway;

import com.red_escuela.backend.billing.dto.PaymentAllocationRequest;
import com.red_escuela.backend.billing.exception.InvalidMpWebhookSignatureException;
import com.red_escuela.backend.billing.infrastructure.MercadoPagoApprovedPayment;
import com.red_escuela.backend.billing.infrastructure.MercadoPagoPaymentFetcher;
import com.red_escuela.backend.billing.infrastructure.MpWebhookSignatureVerifier;
import com.red_escuela.backend.billing.operation.ApprovedPaymentRegistrationPort;
import com.red_escuela.backend.shared.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@UseCase
public class ProcessMpWebhookService implements ProcessMpWebhookUseCase {
    private static final Logger log = LoggerFactory.getLogger(ProcessMpWebhookService.class);

    private final ApprovedPaymentRegistrationPort registrationPort;
    private final MercadoPagoPaymentFetcher mercadoPagoPaymentFetcher;
    private final MpWebhookSignatureVerifier signatureVerifier;

    public ProcessMpWebhookService(
            ApprovedPaymentRegistrationPort registrationPort,
            MercadoPagoPaymentFetcher mercadoPagoPaymentFetcher,
            MpWebhookSignatureVerifier signatureVerifier) {
        this.registrationPort = registrationPort;
        this.mercadoPagoPaymentFetcher = mercadoPagoPaymentFetcher;
        this.signatureVerifier = signatureVerifier;
    }

    @Override
    @Transactional
    public void process(String xSignature, String xRequestId, String mpPaymentId) {
        try {
            signatureVerifier.verify(xSignature, xRequestId, mpPaymentId);
        } catch (InvalidMpWebhookSignatureException e) {
            log.warn("Webhook de Mercado Pago rechazado por firma inválida: pagoMp={}", mpPaymentId);
            throw e;
        }

        registrationPort.lockMercadoPagoPaymentUntilTransactionEnds(mpPaymentId);
        if (registrationPort.isMercadoPagoPaymentRegistered(mpPaymentId)) {
            log.warn("Webhook de Mercado Pago ignorado, el pago ya estaba acreditado: pagoMp={}", mpPaymentId);
            return;
        }

        Optional<MercadoPagoApprovedPayment> approved = mercadoPagoPaymentFetcher.fetchApproved(mpPaymentId);
        if (approved.isEmpty()) {
            log.info("Webhook de Mercado Pago ignorado, el pago no está aprobado: pagoMp={}", mpPaymentId);
            return;
        }

        PaymentAllocationRequest reportedAllocation = approved.get().reportedAllocation();
        registrationPort.registerApprovedMercadoPagoPayment(reportedAllocation.invoiceId(), reportedAllocation.amountApplied(),
                approved.get().installments(), approved.get().cardType(), mpPaymentId);
        log.info("Webhook de Mercado Pago acreditado: pagoMp={}, factura={}, monto={}", mpPaymentId, reportedAllocation.invoiceId(), reportedAllocation.amountApplied());
    }
}
