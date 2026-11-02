package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.exception.InvalidMpWebhookSignatureException;
import com.red_escuela.backend.billing.infrastructure.MpWebhookSignatureVerifier;
import com.red_escuela.backend.billing.payment.domain.MpWebhookReceivedEvent;
import com.red_escuela.backend.billing.payment.repository.MpWebhookReceivedEventRepository;
import com.red_escuela.backend.shared.module.BillingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class MpWebhookRawEventRecorder {
    private static final Logger log = LoggerFactory.getLogger(MpWebhookRawEventRecorder.class);

    private final MpWebhookSignatureVerifier signatureVerifier;
    private final MpWebhookReceivedEventRepository receivedEventRepository;
    private final Clock clock;

    public MpWebhookRawEventRecorder(MpWebhookSignatureVerifier signatureVerifier,
                                     MpWebhookReceivedEventRepository receivedEventRepository, Clock clock) {
        this.signatureVerifier = signatureVerifier;
        this.receivedEventRepository = receivedEventRepository;
        this.clock = clock;
    }

    @Transactional
    public void recordVerifiedEvent(String xSignature, String xRequestId, String mpPaymentId, BillingMode billingMode) {
        try {
            signatureVerifier.verify(xSignature, xRequestId, mpPaymentId);
        } catch (InvalidMpWebhookSignatureException e) {
            log.warn("Webhook de Mercado Pago rechazado por firma inválida: pagoMp={}", mpPaymentId);
            throw e;
        }
        receivedEventRepository.save(MpWebhookReceivedEvent.receivedAt(
                LocalDateTime.now(clock), xSignature, xRequestId, mpPaymentId, billingMode));
        log.info("Webhook de Mercado Pago guardado sin procesar por facturación en modo {}: pagoMp={}", billingMode, mpPaymentId);
    }
}
