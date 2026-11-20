package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.notification.domain.GradesPublishedNotification;
import com.red_escuela.backend.notification.domain.GradeCorrectedNotification;
import com.red_escuela.backend.notification.domain.SubjectApprovalRevertedNotification;
import com.red_escuela.backend.evaluation.events.SubjectApprovalRevertedEvent;
import com.red_escuela.backend.evaluation.events.GradeCorrectedEvent;
import com.red_escuela.backend.evaluation.events.GradesPublishedEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GradeEventsNotificationListener {
    static final String NOTIFY_GRADES_PUBLISHED_HANDLER = "GradeEventsNotificationListener.notifyGradesPublished";
    static final String NOTIFY_GRADE_CORRECTED_HANDLER = "GradeEventsNotificationListener.notifyGradeCorrected";

    static final String NOTIFY_SUBJECT_APPROVAL_REVERTED_HANDLER = "GradeEventsNotificationListener.notifySubjectApprovalReverted";

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public GradeEventsNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher, EntityManager entityManager) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @ApplicationModuleListener
    public void notifyGradesPublished(GradesPublishedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_GRADES_PUBLISHED_HANDLER, () -> eventPublisher.publishEvent(
                new GradesPublishedNotification(event.sectionId(), event.sectionName(), event.gradeTypeLabel(), event.sequenceNumber(),
                        personsOf(event.studentPersonIds()))));
    }

    @ApplicationModuleListener
    public void notifyGradeCorrected(GradeCorrectedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_GRADE_CORRECTED_HANDLER, () -> eventPublisher.publishEvent(
                new GradeCorrectedNotification(event.gradeItemId(), event.sectionName(), event.gradeTypeLabel(), event.sequenceNumber(),
                        entityManager.getReference(Person.class, event.studentPersonId()))));
    }

    @ApplicationModuleListener
    public void notifySubjectApprovalReverted(SubjectApprovalRevertedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_SUBJECT_APPROVAL_REVERTED_HANDLER, () -> eventPublisher.publishEvent(
                new SubjectApprovalRevertedNotification(event.subjectName(), event.missingPrerequisiteNames(),
                        entityManager.getReference(Person.class, event.studentPersonId()))));
    }

    private List<Person> personsOf(List<Long> personIds) {
        return personIds.stream().map(personId -> entityManager.getReference(Person.class, personId)).toList();
    }
}
