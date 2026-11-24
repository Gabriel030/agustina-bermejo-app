package com.red_escuela.backend.notification.invitation.specialist;

import com.red_escuela.backend.identity.api.IssuedActivationToken;
import com.red_escuela.backend.identity.api.GuardianEmailCollisionFinder;
import com.red_escuela.backend.identity.api.PersonContactFinder;
import com.red_escuela.backend.identity.exception.PersonAlreadyActivatedException;
import com.red_escuela.backend.identity.exception.PersonNotEligibleForActivationException;
import com.red_escuela.backend.identity.exception.PersonNotFoundException;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatusReason;
import com.red_escuela.backend.notification.delivery.entity.NotificationDelivery;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import com.red_escuela.backend.notification.invitation.domain.InvitationErrorCode;
import com.red_escuela.backend.notification.invitation.domain.InvitationExclusionReason;
import com.red_escuela.backend.notification.invitation.domain.InvitationRecipientStatus;
import com.red_escuela.backend.notification.invitation.domain.InvitationSendResult;
import com.red_escuela.backend.notification.invitation.domain.InvitationWaveStatus;
import com.red_escuela.backend.notification.invitation.entity.InvitationWave;
import com.red_escuela.backend.notification.invitation.exception.InvitationSendDeferredException;
import com.red_escuela.backend.notification.invitation.entity.InvitationWaveRecipient;
import com.red_escuela.backend.notification.invitation.repository.InvitationWaveRecipientRepository;
import com.red_escuela.backend.notification.invitation.repository.InvitationWaveRepository;
import com.red_escuela.backend.shared.exception.CircuitOpenException;
import com.red_escuela.backend.shared.mail.EmailDeliveryReceipt;
import com.red_escuela.backend.shared.mail.TransactionalEmail;
import com.red_escuela.backend.shared.mail.TransactionalEmailDeliveryException;
import com.red_escuela.backend.shared.mail.TransactionalEmailRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class InvitationRecipientSendTransaction {

    private static final Logger log = LoggerFactory.getLogger(InvitationRecipientSendTransaction.class);

    private final InvitationWaveRepository waveRepository;
    private final InvitationWaveRecipientRepository recipientRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final PersonContactFinder personContactFinder;
    private final GuardianEmailCollisionFinder guardianEmailCollisionFinder;
    private final InvitationActivationTokenRequester tokenRequester;
    private final ActivationInvitationEmailFactory emailFactory;
    private final InvitationMailSender mailSender;
    private final InvitationDeliveryRecorder deliveryRecorder;
    private final InvitationSendingCapacity capacity;
    private final Clock clock;

    public InvitationRecipientSendTransaction(InvitationWaveRepository waveRepository, InvitationWaveRecipientRepository recipientRepository,
                                              NotificationDeliveryRepository deliveryRepository, PersonContactFinder personContactFinder,
                                              GuardianEmailCollisionFinder guardianEmailCollisionFinder,
                                              InvitationActivationTokenRequester tokenRequester, ActivationInvitationEmailFactory emailFactory,
                                              InvitationMailSender mailSender, InvitationDeliveryRecorder deliveryRecorder,
                                              InvitationSendingCapacity capacity, Clock clock) {
        this.waveRepository = waveRepository;
        this.recipientRepository = recipientRepository;
        this.deliveryRepository = deliveryRepository;
        this.personContactFinder = personContactFinder;
        this.guardianEmailCollisionFinder = guardianEmailCollisionFinder;
        this.tokenRequester = tokenRequester;
        this.emailFactory = emailFactory;
        this.mailSender = mailSender;
        this.deliveryRecorder = deliveryRecorder;
        this.capacity = capacity;
        this.clock = clock;
    }

    @Transactional
    public InvitationSendResult sendInvitationToRecipient(Long recipientId) {
        Optional<Long> waveId = recipientRepository.findWaveIdById(recipientId);
        if (waveId.isEmpty()) {
            return InvitationSendResult.NOT_SENDABLE;
        }
        InvitationWave wave = waveRepository.findByIdForUpdate(waveId.get()).orElse(null);
        if (wave == null || wave.getStatus() != InvitationWaveStatus.RUNNING) {
            return InvitationSendResult.NOT_SENDABLE;
        }
        InvitationWaveRecipient recipient = recipientRepository.findByIdForUpdate(recipientId).orElse(null);
        if (recipient == null || !isSendable(recipient)) {
            return InvitationSendResult.NOT_SENDABLE;
        }
        if (personContactFinder.findEmailRecipient(recipient.getPersonId()).isEmpty()) {
            return exclude(recipient, InvitationExclusionReason.NO_EMAIL);
        }
        if (!guardianEmailCollisionFinder.findPersonIdsWithEmailSharedWithGuardian(List.of(recipient.getPersonId())).isEmpty()) {
            return exclude(recipient, InvitationExclusionReason.SHARED_EMAIL);
        }
        if (!capacity.hasCapacityToday(wave)) {
            return InvitationSendResult.NO_QUOTA;
        }
        if (mailSender.isMailChannelRejectingCalls()) {
            return InvitationSendResult.DEFERRED;
        }
        NotificationDelivery delivery = resolveQueuedDelivery(recipient);
        if (delivery == null) {
            return InvitationSendResult.NOT_SENDABLE;
        }
        return issueTokenAndSend(recipient, delivery);
    }

    private boolean isSendable(InvitationWaveRecipient recipient) {
        return recipient.getStatus() == InvitationRecipientStatus.PENDING || recipient.getStatus() == InvitationRecipientStatus.QUEUED;
    }

    private NotificationDelivery resolveQueuedDelivery(InvitationWaveRecipient recipient) {
        if (recipient.getStatus() == InvitationRecipientStatus.PENDING) {
            Optional<NotificationDelivery> recorded = deliveryRecorder.recordQueuedAttempt(recipient);
            recorded.ifPresent(delivery -> recipient.queue(delivery.getId()));
            return recorded.orElse(null);
        }
        NotificationDelivery delivery = deliveryRepository.findById(recipient.getDeliveryId()).orElse(null);
        if (delivery == null || !delivery.getStatus().isWaitingForDispatch()) {
            recipient.markFailed(InvitationErrorCode.UNKNOWN);
            return null;
        }
        return delivery;
    }

    private InvitationSendResult issueTokenAndSend(InvitationWaveRecipient recipient, NotificationDelivery delivery) {
        IssuedActivationToken activation;
        try {
            activation = tokenRequester.requestActivationToken(recipient.getPersonId());
        } catch (PersonAlreadyActivatedException alreadyActivated) {
            deliveryRecorder.discardQueuedDelivery(delivery.getId());
            recipient.markActivated();
            log.info("Invitacion omitida, la persona ya activo waveId={} recipientId={}", recipient.getWaveId(), recipient.getId());
            return InvitationSendResult.ACTIVATED;
        } catch (PersonNotEligibleForActivationException | PersonNotFoundException notEligible) {
            deliveryRecorder.discardQueuedDelivery(delivery.getId());
            return exclude(recipient, InvitationExclusionReason.NOT_ELIGIBLE);
        } catch (RuntimeException tokenFailure) {
            log.warn("No se pudo emitir el token de activacion waveId={} recipientId={} causa={}", recipient.getWaveId(), recipient.getId(),
                    tokenFailure.getClass().getSimpleName());
            delivery.failForGood(DeliveryStatusReason.TOKEN_ISSUE_FAILED, null);
            recipient.markFailed(InvitationErrorCode.TOKEN_ISSUE_FAILED);
            return InvitationSendResult.FAILED;
        }
        TransactionalEmail email = emailFactory.createActivationInvitation(activation);
        return sendEmail(recipient, delivery, email);
    }

    private InvitationSendResult sendEmail(InvitationWaveRecipient recipient, NotificationDelivery delivery, TransactionalEmail email) {
        EmailDeliveryReceipt receipt;
        try {
            receipt = mailSender.sendInvitationEmail(email);
        } catch (CircuitOpenException open) {
            log.warn("Canal de mail cerrado, la invitacion queda diferida waveId={} recipientId={}", recipient.getWaveId(), recipient.getId());
            throw new InvitationSendDeferredException(recipient.getId());
        } catch (TransactionalEmailRejectedException rejection) {
            log.warn("El proveedor rechazo la invitacion waveId={} recipientId={} status={}", recipient.getWaveId(), recipient.getId(), rejection.statusCode());
            delivery.failForGood(DeliveryStatusReason.PROVIDER_REJECTED, "HTTP_" + rejection.statusCode());
            recipient.markFailed(InvitationErrorCode.PROVIDER_REJECTED);
            return InvitationSendResult.FAILED;
        } catch (TransactionalEmailDeliveryException unavailable) {
            log.warn("El proveedor no pudo enviar la invitacion waveId={} recipientId={}", recipient.getWaveId(), recipient.getId());
            delivery.failForGood(DeliveryStatusReason.PROVIDER_ERROR, null);
            recipient.markFailed(InvitationErrorCode.PROVIDER_UNAVAILABLE);
            return InvitationSendResult.FAILED;
        } catch (RuntimeException unknown) {
            log.warn("Fallo inesperado al enviar la invitacion waveId={} recipientId={} causa={}", recipient.getWaveId(), recipient.getId(),
                    unknown.getClass().getSimpleName());
            delivery.failForGood(DeliveryStatusReason.PROVIDER_ERROR, null);
            recipient.markFailed(InvitationErrorCode.UNKNOWN);
            return InvitationSendResult.FAILED;
        }
        Instant now = Instant.now(clock);
        delivery.markSent(receipt.providerMessageId(), receipt.simulated(), now);
        recipient.markSent(now);
        log.info("Invitacion enviada waveId={} recipientId={} personId={}", recipient.getWaveId(), recipient.getId(), recipient.getPersonId());
        return InvitationSendResult.SENT;
    }

    private InvitationSendResult exclude(InvitationWaveRecipient recipient, InvitationExclusionReason reason) {
        deliveryRecorder.discardQueuedDelivery(recipient.getDeliveryId());
        recipient.exclude(reason);
        log.info("Invitacion omitida waveId={} recipientId={} motivo={}", recipient.getWaveId(), recipient.getId(), reason);
        return InvitationSendResult.EXCLUDED;
    }
}
