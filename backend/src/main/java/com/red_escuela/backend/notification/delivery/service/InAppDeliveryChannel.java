package com.red_escuela.backend.notification.delivery.service;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.InAppNotificationChannel;
import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.domain.DeliveryEnvelope;
import com.red_escuela.backend.notification.delivery.entity.NotificationDelivery;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import com.red_escuela.backend.notification.entity.Notification;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import com.red_escuela.backend.shared.time.ApplicationTime;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InAppDeliveryChannel {

    private static final Logger log = LoggerFactory.getLogger(InAppDeliveryChannel.class);

    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationRepository notificationRepository;
    private final EntityManager entityManager;

    public InAppDeliveryChannel(NotificationDeliveryRepository deliveryRepository, NotificationRepository notificationRepository,
                                EntityManager entityManager) {
        this.deliveryRepository = deliveryRepository;
        this.notificationRepository = notificationRepository;
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(DeliveryRequest request) {
        DeliveryEnvelope envelope = request.envelope();
        if (deliveryRepository.existsByDedupKeyAndChannel(envelope.dedupKey(), DeliveryChannel.IN_APP)) {
            return;
        }
        deliveryRepository.saveAndFlush(NotificationDelivery.deliveredInApp(envelope, request.inApp(), ApplicationTime.instant()));
        notificationRepository.save(toNotification(request));
        log.info("Entrega in-app dedup={} kind={} prioridad={} estado=SENT destinatario={}",
                envelope.dedupKey(), envelope.kind(), envelope.priority(), envelope.recipientPersonId());
    }

    private Notification toNotification(DeliveryRequest request) {
        DeliveryEnvelope envelope = request.envelope();
        Notification notification = new Notification();
        notification.setRecipient(entityManager.getReference(Person.class, envelope.recipientPersonId()));
        notification.setType(request.inAppType());
        notification.setMessage(InAppNotificationChannel.abbreviateToColumnLength(request.inApp().bodyText()));
        notification.setRelatedEntityType(envelope.relatedEntityType());
        notification.setRelatedEntityId(envelope.relatedEntityId());
        notification.setCreatedAt(ApplicationTime.now());
        notification.setDedupKey(envelope.dedupKey());
        return notification;
    }
}
