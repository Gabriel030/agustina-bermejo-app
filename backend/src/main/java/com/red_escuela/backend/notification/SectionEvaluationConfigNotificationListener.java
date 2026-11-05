package com.red_escuela.backend.notification;

import com.red_escuela.backend.evaluation.events.SectionEvaluationConfigApprovedEvent;
import com.red_escuela.backend.evaluation.events.SectionEvaluationConfigProposedEvent;
import com.red_escuela.backend.evaluation.events.SectionEvaluationConfigRejectedEvent;
import com.red_escuela.backend.evaluation.events.SectionEvaluationConfigReminderEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class SectionEvaluationConfigNotificationListener {
    static final String NOTIFY_PROPOSAL_HANDLER = "SectionEvaluationConfigNotificationListener.notifyProposal";
    static final String NOTIFY_APPROVAL_HANDLER = "SectionEvaluationConfigNotificationListener.notifyApproval";
    static final String NOTIFY_REJECTION_HANDLER = "SectionEvaluationConfigNotificationListener.notifyRejection";
    static final String NOTIFY_REMINDER_HANDLER = "SectionEvaluationConfigNotificationListener.notifyReminder";
    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public SectionEvaluationConfigNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher, EntityManager entityManager) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @ApplicationModuleListener
    public void notifyProposal(SectionEvaluationConfigProposedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_PROPOSAL_HANDLER, () -> eventPublisher.publishEvent(new SectionEvaluationConfigProposedNotification(event.configId(), event.subjectName(), event.sectionName())));
    }

    @ApplicationModuleListener
    public void notifyApproval(SectionEvaluationConfigApprovedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_APPROVAL_HANDLER, () -> eventPublisher.publishEvent(new SectionEvaluationConfigApprovedNotification(
                event.configId(), event.subjectName(), event.sectionName(), instructorOf(event.instructorPersonId()))));
    }

    @ApplicationModuleListener
    public void notifyRejection(SectionEvaluationConfigRejectedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_REJECTION_HANDLER, () -> eventPublisher.publishEvent(new SectionEvaluationConfigRejectedNotification(
                event.configId(), event.subjectName(), event.sectionName(), event.rejectionReason(), instructorOf(event.instructorPersonId()))));
    }

    @ApplicationModuleListener
    public void notifyReminder(SectionEvaluationConfigReminderEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_REMINDER_HANDLER, () -> eventPublisher.publishEvent(new SectionEvaluationConfigReminderNotification(
                event.configId(), event.subjectName(), event.sectionName(), instructorOf(event.instructorPersonId()), event.daysToEnrollmentStart())));
    }

    private Person instructorOf(Long personId) {
        return entityManager.getReference(Person.class, personId);
    }
}
