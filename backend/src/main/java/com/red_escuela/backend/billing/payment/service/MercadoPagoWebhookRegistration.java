package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.exception.InvalidMpWebhookSignatureException;
import com.red_escuela.backend.billing.infrastructure.MercadoPagoApprovedPayment;
import com.red_escuela.backend.billing.infrastructure.MercadoPagoPaymentFetcher;
import com.red_escuela.backend.billing.infrastructure.MpWebhookSignatureVerifier;
import com.red_escuela.backend.billing.operation.ApprovedPaymentRegistrationPort;
import com.red_escuela.backend.billing.operation.ReportedGatewayAllocation;
import com.red_escuela.backend.shared.logging.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class MercadoPagoWebhookRegistration {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookRegistration.class);

    private final ApprovedPaymentRegistrationPort registrationPort;
    private final MercadoPagoPaymentFetcher mercadoPagoPaymentFetcher;
    private final MpWebhookSignatureVerifier signatureVerifier;

    public MercadoPagoWebhookRegistration(
            ApprovedPaymentRegistrationPort registrationPort,
            MercadoPagoPaymentFetcher mercadoPagoPaymentFetcher,
            MpWebhookSignatureVerifier signatureVerifier) {
        this.registrationPort = registrationPort;
        this.mercadoPagoPaymentFetcher = mercadoPagoPaymentFetcher;
        this.signatureVerifier = signatureVerifier;
    }

    @Transactional
    public void register(String xSignature, String xRequestId, String mpPaymentId) {
        try {
            signatureVerifier.verify(xSignature, xRequestId, mpPaymentId);
        } catch (InvalidMpWebhookSignatureException e) {
            log.warn("Webhook de Mercado Pago rechazado por firma inválida: pagoMp={}", LogSanitizer.sanitize(mpPaymentId));
            throw e;
        }
        registrationPort.lockMercadoPagoPaymentUntilTransactionEnds(mpPaymentId);
        if (registrationPort.isMercadoPagoPaymentRegistered(mpPaymentId)) {
            log.warn("Webhook de Mercado Pago ignorado, el pago ya estaba acreditado: pagoMp={}", LogSanitizer.sanitize(mpPaymentId));
            return;
        }
        Optional<MercadoPagoApprovedPayment> approved = mercadoPagoPaymentFetcher.fetchApproved(mpPaymentId);
        if (approved.isEmpty()) {
            log.info("Webhook de Mercado Pago ignorado, el pago no está aprobado: pagoMp={}", LogSanitizer.sanitize(mpPaymentId));
            return;
        }
        ReportedGatewayAllocation reportedAllocation = approved.get().reportedAllocation();
        registrationPort.registerApprovedMercadoPagoPayment(reportedAllocation.invoiceId(), reportedAllocation.amountApplied(),
                approved.get().installments(), approved.get().cardType(), mpPaymentId);
        log.info("Webhook de Mercado Pago acreditado: pagoMp={}, factura={}, monto={}", mpPaymentId, reportedAllocation.invoiceId(), reportedAllocation.amountApplied());
    }
}
