package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.exception.InvalidMpWebhookSignatureException;
import com.red_escuela.backend.billing.infrastructure.MercadoPagoApprovedPayment;
import com.red_escuela.backend.billing.infrastructure.MercadoPagoPaymentFetcher;
import com.red_escuela.backend.billing.infrastructure.MpWebhookSignatureVerifier;
import com.red_escuela.backend.billing.operation.ApprovedPaymentRegistrationPort;
import com.red_escuela.backend.billing.operation.MercadoPagoCheckoutMatch;
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
        Optional<MercadoPagoCheckoutMatch> checkout = registrationPort.findMercadoPagoCheckout(reportedAllocation.publicReference());
        if (checkout.isEmpty()) {
            log.warn("Webhook de Mercado Pago sin checkout emitido, no se acredita: pagoMp={}, referencia={}",
                    LogSanitizer.sanitize(mpPaymentId), reportedAllocation.publicReference());
            log.error("ALERTA_PASARELA tipo=PAGO_SIN_CHECKOUT pagoMp={} referencia={}", LogSanitizer.sanitize(mpPaymentId), reportedAllocation.publicReference());
            registrationPort.recordMercadoPagoUnmatchedPayment(mpPaymentId, reportedAllocation.publicReference());
            return;
        }
        Long invoiceId = checkout.get().invoiceId();
        if (checkout.get().expectedAmount().currency() != reportedAllocation.amountApplied().currency()) {
            log.error("ALERTA_PASARELA tipo=MONEDA_DISTINTA pagoMp={} factura={} esperado={} cobrado={}", LogSanitizer.sanitize(mpPaymentId), invoiceId,
                    checkout.get().expectedAmount(), reportedAllocation.amountApplied());
            registrationPort.recordMercadoPagoAmountMismatch(invoiceId, checkout.get().expectedAmount(), reportedAllocation.amountApplied(), mpPaymentId);
            return;
        }
        registrationPort.registerApprovedMercadoPagoPayment(invoiceId, reportedAllocation.amountApplied(),
                approved.get().installments(), approved.get().cardType(), mpPaymentId);
        if (!checkout.get().expectedAmount().equals(reportedAllocation.amountApplied())) {
            log.error("ALERTA_PASARELA tipo=MONTO_DISTINTO pagoMp={} factura={} esperado={} cobrado={}", LogSanitizer.sanitize(mpPaymentId), invoiceId,
                    checkout.get().expectedAmount(), reportedAllocation.amountApplied());
            registrationPort.recordMercadoPagoAmountMismatch(invoiceId, checkout.get().expectedAmount(), reportedAllocation.amountApplied(), mpPaymentId);
        }
        if (checkout.get().expired()) {
            log.error("ALERTA_PASARELA tipo=CHECKOUT_VENCIDO pagoMp={} factura={}", LogSanitizer.sanitize(mpPaymentId), invoiceId);
            registrationPort.recordMercadoPagoExpiredCheckout(invoiceId, mpPaymentId);
        }
        if (checkout.get().creditedGatewayPaymentId() != null) {
            log.error("ALERTA_PASARELA tipo=SEGUNDO_PAGO_CHECKOUT pagoMp={} factura={} acreditadoPor={}", LogSanitizer.sanitize(mpPaymentId), invoiceId,
                    checkout.get().creditedGatewayPaymentId());
            registrationPort.recordMercadoPagoDuplicatePaymentForCheckout(invoiceId, checkout.get().creditedGatewayPaymentId(), mpPaymentId);
        } else {
            registrationPort.markMercadoPagoCheckoutCredited(reportedAllocation.publicReference(), mpPaymentId);
        }
        log.info("Webhook de Mercado Pago acreditado: pagoMp={}, factura={}, monto={}", mpPaymentId, invoiceId, reportedAllocation.amountApplied());
    }
}
