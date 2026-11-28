package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.graduation.events.CertificateInvalidatedEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.domain.ClosureNotification;
import com.red_escuela.backend.notification.domain.PersonRecipient;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.service.ProcessedEffectGuard;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CertificateInvalidatedNotificationListener {
    static final String NOTIFY_CERTIFICATE_INVALIDATED_HANDLER = "CertificateInvalidatedNotificationListener.notifyCertificateInvalidated";
    static final String REGULAR_ENTITY_TYPE = "RegularStudentCertificate";
    static final String APPROVED_SUBJECTS_ENTITY_TYPE = "ApprovedSubjectsCertificate";

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public CertificateInvalidatedNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher,
            EntityManager entityManager) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @ApplicationModuleListener
    public void notifyCertificateInvalidated(CertificateInvalidatedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_CERTIFICATE_INVALIDATED_HANDLER, () -> {
            boolean regular = CertificateInvalidatedEvent.REGULAR.equals(event.certificateKind());
            String label = regular ? "de alumno regular" : "de materias aprobadas";
            eventPublisher.publishEvent(new ClosureNotification(NotificationType.CERTIFICATE_INVALIDATED,
                    "Tu constancia " + label + " dejó de ser válida. Motivo: " + event.reason()
                            + ". Si todavía la necesitás, pedí una nueva desde Mis constancias: se emite una vez que se cumplan de nuevo las condiciones y respetando el límite diario de emisión por tipo de constancia.",
                    List.of(new PersonRecipient(entityManager.getReference(Person.class, event.personId()))),
                    regular ? REGULAR_ENTITY_TYPE : APPROVED_SUBJECTS_ENTITY_TYPE, event.certificateId()));
        });
    }
}
