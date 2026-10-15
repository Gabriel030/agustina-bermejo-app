package com.red_escuela.backend.notification.delivery.service;

import com.red_escuela.backend.identity.api.EmailRecipient;
import com.red_escuela.backend.identity.api.EmailRecipientLookup;
import com.red_escuela.backend.identity.api.NotificationPreferenceQuery;
import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.domain.DeliveryEnvelope;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatus;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatusReason;
import com.red_escuela.backend.notification.delivery.entity.NotificationDelivery;
import com.red_escuela.backend.notification.delivery.exception.NotificationDeliveryNotFoundException;
import com.red_escuela.backend.notification.delivery.policy.EmailDeliveryContext;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.mail.EmailDeliveryReceipt;
import com.red_escuela.backend.shared.mail.MaskedEmailAddress;
import com.red_escuela.backend.shared.mail.TransactionalEmail;
import com.red_escuela.backend.shared.mail.TransactionalEmailGateway;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class EmailDeliveryChannel {

    public static final int MAX_SEND_ATTEMPTS = 3;

    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryChannel.class);

    private final NotificationDeliveryRepository deliveryRepository;
    private final EmailRecipientLookup emailRecipientLookup;
    private final NotificationPreferenceQuery notificationPreferenceQuery;
    private final PolicySet<EmailDeliveryContext> emailDeliveryPolicySet;
    private final TransactionalEmailGateway gateway;

    public EmailDeliveryChannel(NotificationDeliveryRepository deliveryRepository, EmailRecipientLookup emailRecipientLookup,
                                NotificationPreferenceQuery notificationPreferenceQuery,
                                PolicySet<EmailDeliveryContext> emailDeliveryPolicySet, TransactionalEmailGateway gateway) {
        this.deliveryRepository = deliveryRepository;
        this.emailRecipientLookup = emailRecipientLookup;
        this.notificationPreferenceQuery = notificationPreferenceQuery;
        this.emailDeliveryPolicySet = emailDeliveryPolicySet;
        this.gateway = gateway;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(DeliveryRequest request) {
        DeliveryEnvelope envelope = request.envelope();
        if (deliveryRepository.existsByDedupKeyAndChannel(envelope.dedupKey(), DeliveryChannel.EMAIL)) {
            return;
        }
        Long personId = envelope.recipientPersonId();
        boolean verified = emailRecipientLookup.findEmailRecipient(personId).map(EmailRecipient::verified).orElse(false);
        EmailDeliveryContext context = new EmailDeliveryContext(request.emailPrecedesDueDate(),
                notificationPreferenceQuery.isPreDueReminderEmailEnabled(personId), verified);
        var denial = emailDeliveryPolicySet.evaluateRulesUntilFirstViolation(context).firstDenial();
        NotificationDelivery delivery = denial
                .map(verdict -> NotificationDelivery.suppressedEmail(envelope, request.email(), verdict.code(), ApplicationTime.instant()))
                .orElseGet(() -> NotificationDelivery.queuedEmail(envelope, request.email(), ApplicationTime.instant()));
        deliveryRepository.saveAndFlush(delivery);
        log.info("Entrega mail dedup={} kind={} prioridad={} estado={} motivo={} destinatario={}",
                envelope.dedupKey(), envelope.kind(), envelope.priority(), delivery.getStatus(), delivery.getStatusReason(), personId);
    }

    @Transactional
    public void dispatchDelivery(Long deliveryId) {
        Optional<NotificationDelivery> locked = deliveryRepository.findByIdForUpdate(deliveryId);
        if (locked.isEmpty()) {
            if (!deliveryRepository.existsById(deliveryId)) {
                throw new NotificationDeliveryNotFoundException(deliveryId);
            }
            log.info("Entrega {} en manos de otro worker, se omite", deliveryId);
            return;
        }
        NotificationDelivery delivery = locked.get();
        if (!delivery.getStatus().isWaitingForDispatch()) {
            return;
        }
        Optional<EmailRecipient> recipient = emailRecipientLookup.findEmailRecipient(delivery.getRecipientPersonId())
                .filter(EmailRecipient::verified);
        if (recipient.isEmpty()) {
            delivery.suppress(DeliveryStatusReason.NO_VERIFIED_EMAIL);
            logOutcome(delivery, null);
            return;
        }
        TransactionalEmail email = new TransactionalEmail(recipient.get().address(), delivery.getSubject(), delivery.getBodyText(), delivery.getBodyHtml());
        EmailDeliveryReceipt receipt = null;
        try {
            receipt = gateway.send(email);
        } catch (RuntimeException failure) {
            log.warn("El proveedor de mail fallo dedup={} causa={}", delivery.getDedupKey(), failure.getClass().getSimpleName());
        }
        if (receipt == null) {
            delivery.recordFailedAttempt(DeliveryStatusReason.PROVIDER_ERROR, MAX_SEND_ATTEMPTS);
        } else {
            delivery.markSent(receipt.providerMessageId(), receipt.simulated(), ApplicationTime.instant());
        }
        logOutcome(delivery, MaskedEmailAddress.of(recipient.get().address()));
    }

    private void logOutcome(NotificationDelivery delivery, String maskedRecipient) {
        if (delivery.getStatus() == DeliveryStatus.FAILED) {
            log.warn("Mail de cobranza fallido tras {} intentos dedup={} kind={} destinatario={}",
                    delivery.getAttempts(), delivery.getDedupKey(), delivery.getKind(), maskedRecipient);
            return;
        }
        log.info("Envio mail dedup={} kind={} prioridad={} estado={} intentos={} destinatario={}",
                delivery.getDedupKey(), delivery.getKind(), delivery.getPriority(), delivery.getStatus(), delivery.getAttempts(), maskedRecipient);
    }
}
