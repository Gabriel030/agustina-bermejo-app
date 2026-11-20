package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.graduation.events.RegularCertificateRequestFulfilledEvent;
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
public class RegularCertificateReadyNotificationListener {
    static final String NOTIFY_CERTIFICATE_READY_HANDLER = "RegularCertificateReadyNotificationListener.notifyCertificateReady";
    static final String CERTIFICATE_ENTITY_TYPE = "RegularStudentCertificate";

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public RegularCertificateReadyNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher,
            EntityManager entityManager) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @ApplicationModuleListener
    public void notifyCertificateReady(RegularCertificateRequestFulfilledEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_CERTIFICATE_READY_HANDLER,
                () -> eventPublisher.publishEvent(new ClosureNotification(NotificationType.REGULAR_CERTIFICATE_READY,
                        "Tu constancia de alumno regular ya está lista para descargar.",
                        List.of(new PersonRecipient(entityManager.getReference(Person.class, event.personId()))),
                        CERTIFICATE_ENTITY_TYPE, event.certificateId())));
    }
}
