package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.graduation.events.CertificateExceptionCreatedByDirectorEvent;
import com.red_escuela.backend.graduation.events.CertificateExceptionDecidedEvent;
import com.red_escuela.backend.graduation.events.CertificateExceptionRequestedEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.domain.ClosureNotification;
import com.red_escuela.backend.notification.domain.NotificationTopic;
import com.red_escuela.backend.notification.domain.PersonRecipient;
import com.red_escuela.backend.notification.domain.RoleRecipient;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CertificateExceptionNotificationListener {
    static final String NOTIFY_EXCEPTION_REQUESTED_HANDLER = "CertificateExceptionNotificationListener.notifyExceptionRequested";
    static final String NOTIFY_EXCEPTION_DECIDED_HANDLER = "CertificateExceptionNotificationListener.notifyExceptionDecided";
    static final String NOTIFY_EXCEPTION_DIRECT_CREATED_HANDLER = "CertificateExceptionNotificationListener.notifyExceptionCreatedByDirector";
    static final String EXCEPTION_ENTITY_TYPE = "CertificateExceptionApproval";

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public CertificateExceptionNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher,
            EntityManager entityManager) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @ApplicationModuleListener
    public void notifyExceptionRequested(CertificateExceptionRequestedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_EXCEPTION_REQUESTED_HANDLER,
                () -> eventPublisher.publishEvent(new ClosureNotification(NotificationType.CERTIFICATE_EXCEPTION_REQUESTED,
                        "Hay un pedido de excepción para emitir una constancia de alumno regular pendiente de tu decisión.",
                        List.of(NotificationTopic.DIRECTOR_APPROVALS), EXCEPTION_ENTITY_TYPE, event.exceptionId())));
    }

    @ApplicationModuleListener
    public void notifyExceptionDecided(CertificateExceptionDecidedEvent event) {
        String outcome = event.approved() ? "aprobado" : "rechazado";
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_EXCEPTION_DECIDED_HANDLER,
                () -> eventPublisher.publishEvent(new ClosureNotification(NotificationType.CERTIFICATE_EXCEPTION_DECIDED,
                        "Tu pedido de excepción para la constancia de alumno regular fue " + outcome + ". Motivo: " + event.decisionReason(),
                        List.of(new PersonRecipient(entityManager.getReference(Person.class, event.requesterPersonId()))),
                        EXCEPTION_ENTITY_TYPE, event.exceptionId())));
    }

    @ApplicationModuleListener
    public void notifyExceptionCreatedByDirector(CertificateExceptionCreatedByDirectorEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_EXCEPTION_DIRECT_CREATED_HANDLER,
                () -> eventPublisher.publishEvent(new ClosureNotification(NotificationType.CERTIFICATE_EXCEPTION_DIRECT_CREATED,
                        "El Director creó una excepción aprobada para emitir una constancia de alumno regular.",
                        List.of(new RoleRecipient("ADMINISTRATIVO")), EXCEPTION_ENTITY_TYPE, event.exceptionId())));
    }
}
