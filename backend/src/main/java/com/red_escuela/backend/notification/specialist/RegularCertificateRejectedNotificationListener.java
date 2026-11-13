package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.graduation.events.RegularCertificateRequestRejectedEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.domain.ClosureNotification;
import com.red_escuela.backend.notification.domain.PersonRecipient;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RegularCertificateRejectedNotificationListener {
    static final String NOTIFY_CERTIFICATE_REJECTED_HANDLER = "RegularCertificateRejectedNotificationListener.notifyCertificateRejected";
    static final String REQUEST_ENTITY_TYPE = "RegularCertificateRequest";

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public RegularCertificateRejectedNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher,
            EntityManager entityManager) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @ApplicationModuleListener
    public void notifyCertificateRejected(RegularCertificateRequestRejectedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_CERTIFICATE_REJECTED_HANDLER,
                () -> eventPublisher.publishEvent(new ClosureNotification(NotificationType.REGULAR_CERTIFICATE_REJECTED,
                        "Tu pedido de constancia de alumno regular fue rechazado. Motivo: " + event.reason(),
                        List.of(new PersonRecipient(entityManager.getReference(Person.class, event.personId()))),
                        REQUEST_ENTITY_TYPE, event.requestId())));
    }
}
