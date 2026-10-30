package com.red_escuela.backend.notification;

import com.red_escuela.backend.evaluation.events.SectionEvaluationConfigApprovedEvent;
import com.red_escuela.backend.evaluation.events.SectionEvaluationConfigProposedEvent;
import com.red_escuela.backend.evaluation.events.SectionEvaluationConfigRejectedEvent;
import com.red_escuela.backend.evaluation.events.SectionEvaluationConfigReminderEvent;
import com.red_escuela.backend.identity.entity.Person;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class SectionEvaluationConfigNotificationListener {
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public SectionEvaluationConfigNotificationListener(ApplicationEventPublisher eventPublisher, EntityManager entityManager) {
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @EventListener
    public void notifyProposal(SectionEvaluationConfigProposedEvent event) {
        eventPublisher.publishEvent(new SectionEvaluationConfigProposedNotification(event.configId(), event.subjectName(), event.sectionName()));
    }

    @EventListener
    public void notifyApproval(SectionEvaluationConfigApprovedEvent event) {
        eventPublisher.publishEvent(new SectionEvaluationConfigApprovedNotification(
                event.configId(), event.subjectName(), event.sectionName(), instructorOf(event.instructorPersonId())));
    }

    @EventListener
    public void notifyRejection(SectionEvaluationConfigRejectedEvent event) {
        eventPublisher.publishEvent(new SectionEvaluationConfigRejectedNotification(
                event.configId(), event.subjectName(), event.sectionName(), event.rejectionReason(), instructorOf(event.instructorPersonId())));
    }

    @EventListener
    public void notifyReminder(SectionEvaluationConfigReminderEvent event) {
        eventPublisher.publishEvent(new SectionEvaluationConfigReminderNotification(
                event.configId(), event.subjectName(), event.sectionName(), instructorOf(event.instructorPersonId()), event.daysToEnrollmentStart()));
    }

    private Person instructorOf(Long personId) {
        return entityManager.getReference(Person.class, personId);
    }
}
