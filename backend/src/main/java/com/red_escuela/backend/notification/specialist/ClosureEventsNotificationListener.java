package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.api.ClosureNotificationTargets;
import com.red_escuela.backend.evaluation.api.ClosureNotificationTargetsFinder;
import com.red_escuela.backend.evaluation.events.ClosureApprovedEvent;
import com.red_escuela.backend.evaluation.events.ClosureConfirmationReminderEvent;
import com.red_escuela.backend.evaluation.events.ClosureExceptionGrantedEvent;
import com.red_escuela.backend.evaluation.events.ClosureReopenedEvent;
import com.red_escuela.backend.evaluation.events.ExamBoardAutoClosedEvent;
import com.red_escuela.backend.evaluation.events.ExamBoardWithoutTribunalEvent;
import com.red_escuela.backend.evaluation.events.TermEndClosureOverdueEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.domain.ClosureNotification;
import com.red_escuela.backend.notification.domain.NotificationRecipient;
import com.red_escuela.backend.notification.domain.NotificationTopic;
import com.red_escuela.backend.notification.domain.PersonRecipient;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.service.ProcessedEffectGuard;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ClosureEventsNotificationListener {
    static final String NOTIFY_CLOSURE_APPROVED_HANDLER = "ClosureEventsNotificationListener.notifyClosureApproved";
    static final String NOTIFY_CLOSURE_REOPENED_HANDLER = "ClosureEventsNotificationListener.notifyClosureReopened";
    static final String NOTIFY_CLOSURE_EXCEPTION_GRANTED_HANDLER = "ClosureEventsNotificationListener.notifyClosureExceptionGranted";
    static final String NOTIFY_CLOSURE_REMINDER_HANDLER = "ClosureEventsNotificationListener.notifyClosureConfirmationReminder";
    static final String NOTIFY_TERM_END_OVERDUE_HANDLER = "ClosureEventsNotificationListener.notifyTermEndClosureOverdue";
    static final String NOTIFY_EXAM_BOARD_AUTO_CLOSED_HANDLER = "ClosureEventsNotificationListener.notifyExamBoardAutoClosed";
    static final String NOTIFY_EXAM_BOARD_WITHOUT_TRIBUNAL_HANDLER = "ClosureEventsNotificationListener.notifyExamBoardWithoutTribunal";
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

    @ApplicationModuleListener
    public void notifyTermEndClosureOverdue(TermEndClosureOverdueEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_TERM_END_OVERDUE_HANDLER, () -> {
            String entityType = "PROGRAM".equals(event.sectionKind()) ? "SubjectSection" : "CourseSubjectSection";
            eventPublisher.publishEvent(new ClosureNotification(NotificationType.TERM_CLOSURE_OVERDUE,
                    "Pasaron " + event.daysAfterTermEnd() + " días del fin de la cursada de " + event.sectionLabel()
                            + " y el cierre sigue pendiente: terminó el plazo de gracia.",
                    List.of(NotificationTopic.TERM_CLOSURE_SUPERVISION), entityType, event.sectionId()));
            personRecipientsOfSection(event).ifPresent(recipients -> eventPublisher.publishEvent(new ClosureNotification(
                    NotificationType.TERM_CLOSURE_OVERDUE,
                    "Terminó el plazo de gracia para cerrar " + event.sectionLabel() + ": Dirección ya fue avisada.",
                    recipients, entityType, event.sectionId())));
        });
    }

    @ApplicationModuleListener
    public void notifyExamBoardAutoClosed(ExamBoardAutoClosedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_EXAM_BOARD_AUTO_CLOSED_HANDLER, () ->
                eventPublisher.publishEvent(new ClosureNotification(NotificationType.EXAM_BOARD_AUTO_CLOSED,
                        "La mesa de " + event.subjectName() + " se cerró sola al terminar el plazo de gracia: "
                                + event.absentCount() + " inscriptos sin nota quedaron ausentes.",
                        List.of(NotificationTopic.TERM_CLOSURE_SUPERVISION), "ExamBoard", event.examBoardId())));
    }

    @ApplicationModuleListener
    public void notifyExamBoardWithoutTribunal(ExamBoardWithoutTribunalEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_EXAM_BOARD_WITHOUT_TRIBUNAL_HANDLER, () ->
                eventPublisher.publishEvent(new ClosureNotification(NotificationType.TERM_CLOSURE_OVERDUE,
                        "La mesa de " + event.subjectName() + " venció el plazo de gracia pero no se puede cerrar sola: no tiene tribunal asignado.",
                        List.of(NotificationTopic.TERM_CLOSURE_SUPERVISION), "ExamBoard", event.examBoardId())));
    }

    private Optional<List<NotificationRecipient>> personRecipientsOfSection(TermEndClosureOverdueEvent event) {
        List<Long> instructors = targetsFinder.findInstructorPersonIds(
                SectionKind.valueOf(event.sectionKind()), event.sectionId());
        return instructors.isEmpty() ? Optional.empty() : Optional.of(personRecipients(instructors));
    }

    private List<NotificationRecipient> personRecipients(List<Long> personIds) {
        return personIds.stream().map(this::personRecipient).toList();
    }

    private NotificationRecipient personRecipient(Long personId) {
        return new PersonRecipient(entityManager.getReference(Person.class, personId));
    }
}
