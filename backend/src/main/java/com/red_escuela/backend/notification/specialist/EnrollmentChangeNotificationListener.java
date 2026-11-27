package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.enrollment.events.SubjectEnrollmentWithdrawnEvent;
import com.red_escuela.backend.enrollment.events.SubjectSectionChangedEvent;
import com.red_escuela.backend.enrollment.events.SubjectWaitlistPromotedEvent;
import com.red_escuela.backend.enrollment.events.WaitlistEntryExpiredEvent;
import com.red_escuela.backend.enrollment.events.WaitlistEntryPausedEvent;
import com.red_escuela.backend.enrollment.events.WaitlistEntryPromotedEvent;
import com.red_escuela.backend.enrollment.events.WaitlistEntryRemovedEvent;
import com.red_escuela.backend.enrollment.events.WaitlistExpiryReminderEvent;
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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Component
public class EnrollmentChangeNotificationListener {
    static final String NOTIFY_SUBJECT_WITHDRAWN_HANDLER = "EnrollmentChangeNotificationListener.notifySubjectWithdrawn";
    static final String NOTIFY_SECTION_CHANGED_HANDLER = "EnrollmentChangeNotificationListener.notifySectionChanged";
    static final String NOTIFY_WAITLIST_PROMOTED_HANDLER = "EnrollmentChangeNotificationListener.notifyWaitlistPromoted";
    static final String NOTIFY_ENTRY_PROMOTED_HANDLER = "EnrollmentChangeNotificationListener.notifyWaitlistEntryPromoted";
    static final String NOTIFY_ENTRY_PAUSED_HANDLER = "EnrollmentChangeNotificationListener.notifyWaitlistEntryPaused";
    static final String NOTIFY_ENTRY_REMOVED_HANDLER = "EnrollmentChangeNotificationListener.notifyWaitlistEntryRemoved";
    static final String NOTIFY_ENTRY_EXPIRED_HANDLER = "EnrollmentChangeNotificationListener.notifyWaitlistEntryExpired";
    static final String NOTIFY_EXPIRY_REMINDER_HANDLER = "EnrollmentChangeNotificationListener.notifyWaitlistExpiryReminder";
    static final String SECTION_ENROLLMENT_ENTITY_TYPE = "SubjectSectionEnrollment";

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public EnrollmentChangeNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher,
            EntityManager entityManager) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @ApplicationModuleListener
    public void notifySubjectWithdrawn(SubjectEnrollmentWithdrawnEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_SUBJECT_WITHDRAWN_HANDLER, () -> {
            String reasonSuffix = event.reason() == null ? "" : " Motivo: " + event.reason();
            if (event.notifyStudent()) {
                publish(NotificationType.SUBJECT_WITHDRAWN,
                        "Te dieron de baja de " + event.subjectName() + " (" + event.sectionName() + ")." + reasonSuffix,
                        List.of(event.studentPersonId()), event.enrollmentId());
            }
            publish(NotificationType.SUBJECT_WITHDRAWN_TEACHER,
                    event.studentName() + " ya no cursa " + event.subjectName() + " en la comisión " + event.sectionName() + "." + reasonSuffix,
                    event.instructorPersonIds(), event.enrollmentId());
        });
    }

    @ApplicationModuleListener
    public void notifySectionChanged(SubjectSectionChangedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_SECTION_CHANGED_HANDLER, () -> {
            String reasonSuffix = event.reason() == null ? "" : " Motivo: " + event.reason();
            if (event.notifyStudent()) {
                publish(NotificationType.SECTION_CHANGED,
                        "Te cambiaron de comisión en " + event.subjectName() + ": de " + event.fromSectionName() + " a "
                                + event.toSectionName() + "." + reasonSuffix,
                        List.of(event.studentPersonId()), event.enrollmentId());
            }
            publish(NotificationType.SECTION_CHANGED_TEACHER,
                    event.studentName() + " dejó la comisión " + event.fromSectionName() + " de " + event.subjectName() + "." + reasonSuffix,
                    event.fromInstructorPersonIds(), event.enrollmentId());
            publish(NotificationType.SECTION_CHANGED_TEACHER,
                    event.studentName() + " se sumó a la comisión " + event.toSectionName() + " de " + event.subjectName() + "." + reasonSuffix,
                    event.toInstructorPersonIds(), event.enrollmentId());
        });
    }

    @ApplicationModuleListener
    public void notifyWaitlistPromoted(SubjectWaitlistPromotedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_WAITLIST_PROMOTED_HANDLER, () ->
                publish(NotificationType.WAITLIST_PROMOTED,
                        "Se liberó un lugar y pasaste de la lista de espera a cursar " + event.subjectName() + " (" + event.sectionName() + ").",
                        List.of(event.studentPersonId()), event.enrollmentId()));
    }

    @ApplicationModuleListener
    public void notifyWaitlistEntryPromoted(WaitlistEntryPromotedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_ENTRY_PROMOTED_HANDLER, () ->
                publish(NotificationType.WAITLIST_PROMOTED,
                        "Se liberó un lugar y pasaste de la lista de espera a cursar " + event.queueName() + ".",
                        List.of(event.studentPersonId()), event.enrollmentId()));
    }

    @ApplicationModuleListener
    public void notifyWaitlistEntryPaused(WaitlistEntryPausedEvent event) {
        DerivedEventIdentity pairIdentity = new DerivedEventIdentity(pausedPairId(event), event.correlationId(), event.occurredAt());
        effectGuard.runEffectOnce(pairIdentity, NOTIFY_ENTRY_PAUSED_HANDLER, () ->
                publish(NotificationType.WAITLIST_PAUSED_BY_SCHEDULE_OVERLAP,
                        "Tu lugar en la lista de espera de " + event.queueName() + " está en pausa porque se superpone con "
                                + event.conflictingSectionName() + ". Conservás tu posición: cuando se resuelva el conflicto vas a poder ser promovido.",
                        List.of(event.studentPersonId()), event.enrollmentId()));
    }

    @ApplicationModuleListener
    public void notifyWaitlistEntryRemoved(WaitlistEntryRemovedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_ENTRY_REMOVED_HANDLER, () ->
                publish(NotificationType.WAITLIST_REMOVED_BY_STAFF,
                        "Te quitamos de la lista de espera de " + event.queueName() + ". Consultá con la institución si tenés dudas.",
                        List.of(event.studentPersonId()), event.enrollmentId()));
    }

    @ApplicationModuleListener
    public void notifyWaitlistEntryExpired(WaitlistEntryExpiredEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_ENTRY_EXPIRED_HANDLER, () ->
                publish(NotificationType.WAITLIST_ENTRY_EXPIRED,
                        "Venció tu lugar en la lista de espera de " + event.queueName() + " porque cerró la inscripción.",
                        List.of(event.studentPersonId()), event.enrollmentId()));
    }

    @ApplicationModuleListener
    public void notifyWaitlistExpiryReminder(WaitlistExpiryReminderEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_EXPIRY_REMINDER_HANDLER, () ->
                publish(NotificationType.WAITLIST_EXPIRING_SOON,
                        "Tu lugar en la lista de espera de " + event.queueName() + " vence el " + event.closesOn() + " si no se libera un cupo.",
                        List.of(event.studentPersonId()), event.enrollmentId()));
    }

    private static UUID pausedPairId(WaitlistEntryPausedEvent event) {
        String pair = WaitlistEntryPausedEvent.class.getName() + "|" + event.enrollmentId() + "|" + event.conflictingSectionName();
        return UUID.nameUUIDFromBytes(pair.getBytes(StandardCharsets.UTF_8));
    }

    private void publish(NotificationType type, String message, List<Long> personIds, Long enrollmentId) {
        if (personIds.isEmpty()) {
            return;
        }
        List<NotificationRecipient> recipients = personIds.stream().distinct()
                .<NotificationRecipient>map(personId -> new PersonRecipient(entityManager.getReference(Person.class, personId)))
                .toList();
        eventPublisher.publishEvent(new ClosureNotification(type, message, recipients, SECTION_ENROLLMENT_ENTITY_TYPE, enrollmentId));
    }
}
