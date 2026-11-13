package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.evaluation.api.ClosureNotificationTargets;
import com.red_escuela.backend.evaluation.api.ClosureNotificationTargetsFinder;
import com.red_escuela.backend.evaluation.events.ClosureApprovedEvent;
import com.red_escuela.backend.evaluation.events.ClosureConfirmationReminderEvent;
import com.red_escuela.backend.evaluation.events.ClosureExceptionGrantedEvent;
import com.red_escuela.backend.evaluation.events.ClosureReopenedEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.domain.ClosureNotification;
import com.red_escuela.backend.notification.domain.NotificationRecipient;
import com.red_escuela.backend.notification.domain.NotificationTopic;
import com.red_escuela.backend.notification.domain.PersonRecipient;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClosureEventsNotificationListener {
    static final String NOTIFY_CLOSURE_APPROVED_HANDLER = "ClosureEventsNotificationListener.notifyClosureApproved";
    static final String NOTIFY_CLOSURE_REOPENED_HANDLER = "ClosureEventsNotificationListener.notifyClosureReopened";
    static final String NOTIFY_CLOSURE_EXCEPTION_GRANTED_HANDLER = "ClosureEventsNotificationListener.notifyClosureExceptionGranted";
    static final String NOTIFY_CLOSURE_REMINDER_HANDLER = "ClosureEventsNotificationListener.notifyClosureConfirmationReminder";
    static final String CLOSURE_ENTITY_TYPE = "CommissionClosure";

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;
    private final ClosureNotificationTargetsFinder targetsFinder;

    public ClosureEventsNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher,
            EntityManager entityManager, ClosureNotificationTargetsFinder targetsFinder) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
        this.targetsFinder = targetsFinder;
    }

    @ApplicationModuleListener
    public void notifyClosureApproved(ClosureApprovedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_CLOSURE_APPROVED_HANDLER, () -> {
            ClosureNotificationTargets targets = targetsFinder.findClosureTargets(event.closureId());
            targets.students().forEach(student -> eventPublisher.publishEvent(new ClosureNotification(
                    NotificationType.CLOSURE_APPROVED,
                    "Se cerró el acta de " + targets.sectionLabel() + ": tu resultado es «" + student.resultLabel() + "».",
                    List.of(personRecipient(student.personId())), CLOSURE_ENTITY_TYPE, event.closureId())));
            eventPublisher.publishEvent(new ClosureNotification(NotificationType.CLOSURE_APPROVED_SUMMARY,
                    "Se cerró el acta " + event.reportNumber() + " de " + targets.sectionLabel() + ": " + targets.promoted()
                            + " promocionan y " + targets.goingToFinal() + " van a final.",
                    personRecipients(targets.instructorPersonIds()), CLOSURE_ENTITY_TYPE, event.closureId()));
        });
    }

    @ApplicationModuleListener
    public void notifyClosureReopened(ClosureReopenedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_CLOSURE_REOPENED_HANDLER, () -> {
            ClosureNotificationTargets targets = targetsFinder.findClosureTargets(event.closureId());
            eventPublisher.publishEvent(new ClosureNotification(NotificationType.CLOSURE_REOPENED,
                    "Se reabrió el acta " + event.reportNumber() + " de " + targets.sectionLabel() + ": " + event.reason()
                            + ". Tenés que volver a confirmar el cierre.",
                    personRecipients(targets.instructorPersonIds()), CLOSURE_ENTITY_TYPE, event.closureId()));
            eventPublisher.publishEvent(new ClosureNotification(NotificationType.CLOSURE_REOPENED,
                    "Se reabrió el acta de " + targets.sectionLabel() + " para corregirla: tu resultado puede cambiar. Te avisamos cuando se cierre de nuevo.",
                    personRecipients(targets.students().stream().map(student -> student.personId()).toList()),
                    CLOSURE_ENTITY_TYPE, event.closureId()));
        });
    }

    @ApplicationModuleListener
    public void notifyClosureExceptionGranted(ClosureExceptionGrantedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_CLOSURE_EXCEPTION_GRANTED_HANDLER, () -> {
            ClosureNotificationTargets targets = targetsFinder.findClosureTargets(event.closureId());
            eventPublisher.publishEvent(new ClosureNotification(NotificationType.CLOSURE_EXCEPTION_GRANTED,
                    "El Director registró una excepción de promoción en " + targets.sectionLabel() + ": " + event.reason(),
                    List.of(NotificationTopic.CLOSURE_SECRETARIAT), CLOSURE_ENTITY_TYPE, event.closureId()));
            eventPublisher.publishEvent(new ClosureNotification(NotificationType.CLOSURE_EXCEPTION_GRANTED_TEACHER,
                    "La planilla del cierre de " + targets.sectionLabel() + " cambió: el Director registró una excepción de promoción.",
                    personRecipients(targets.instructorPersonIds()), CLOSURE_ENTITY_TYPE, event.closureId()));
        });
    }

    @ApplicationModuleListener
    public void notifyClosureConfirmationReminder(ClosureConfirmationReminderEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_CLOSURE_REMINDER_HANDLER, () ->
                eventPublisher.publishEvent(new ClosureNotification(NotificationType.CLOSURE_CONFIRMATION_REMINDER,
                        "Pasaron " + event.daysAfterTermEnd() + " días del fin de la cursada de " + event.sectionLabel()
                                + " y todavía no confirmaste el cierre de la comisión.",
                        personRecipients(event.instructorPersonIds()), "PROGRAM".equals(event.sectionKind()) ? "SubjectSection" : "CourseSubjectSection", event.sectionId())));
    }

    private List<NotificationRecipient> personRecipients(List<Long> personIds) {
        return personIds.stream().map(this::personRecipient).toList();
    }

    private NotificationRecipient personRecipient(Long personId) {
        return new PersonRecipient(entityManager.getReference(Person.class, personId));
    }
}
