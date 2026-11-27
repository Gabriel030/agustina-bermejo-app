package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.enrollment.events.SubjectEquivalenceRecordedEvent;
import com.red_escuela.backend.enrollment.events.SubjectEquivalenceVoidedEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.domain.ClosureNotification;
import com.red_escuela.backend.notification.domain.NotificationRecipient;
import com.red_escuela.backend.notification.domain.PersonRecipient;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.service.ProcessedEffectGuard;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SubjectEquivalenceNotificationListener {
    static final String NOTIFY_RECORDED_HANDLER = "SubjectEquivalenceNotificationListener.notifyEquivalenceRecorded";
    static final String NOTIFY_VOIDED_HANDLER = "SubjectEquivalenceNotificationListener.notifyEquivalenceVoided";
    static final String ENTITY_TYPE = "SubjectEquivalence";
    static final String FALLBACK_SUBJECT_LABEL = "una materia";

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public SubjectEquivalenceNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher,
            EntityManager entityManager) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @ApplicationModuleListener
    public void notifyEquivalenceRecorded(SubjectEquivalenceRecordedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_RECORDED_HANDLER, () ->
                publish(NotificationType.SUBJECT_EQUIVALENCE_RECORDED, recordedMessage(event.subjectName()),
                        event.studentPersonId(), event.subjectEquivalenceId()));
    }

    @ApplicationModuleListener
    public void notifyEquivalenceVoided(SubjectEquivalenceVoidedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_VOIDED_HANDLER, () ->
                publish(NotificationType.SUBJECT_EQUIVALENCE_VOIDED, voidedMessage(event.subjectName()),
                        event.studentPersonId(), event.subjectEquivalenceId()));
    }

    static String recordedMessage(String subjectName) {
        return "Te reconocieron la equivalencia de " + labelOf(subjectName) + ": ahora figura como aprobada en tu plan de estudio.";
    }

    static String voidedMessage(String subjectName) {
        return "Se anuló la equivalencia de " + labelOf(subjectName) + ". Consultá con la institución si tenés dudas.";
    }

    private static String labelOf(String subjectName) {
        return subjectName == null || subjectName.isBlank() ? FALLBACK_SUBJECT_LABEL : subjectName;
    }

    private void publish(NotificationType type, String message, Long studentPersonId, Long equivalenceId) {
        List<NotificationRecipient> recipients = List.of(new PersonRecipient(entityManager.getReference(Person.class, studentPersonId)));
        eventPublisher.publishEvent(new ClosureNotification(type, message, recipients, ENTITY_TYPE, equivalenceId));
    }
}
