package com.red_escuela.backend.notification;

import com.red_escuela.backend.evaluation.events.ExamBoardCanceledEvent;
import com.red_escuela.backend.evaluation.events.ExamBoardRescheduledEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExamBoardEventsNotificationListener {
    static final String NOTIFY_EXAM_BOARD_RESCHEDULED_HANDLER = "ExamBoardEventsNotificationListener.notifyExamBoardRescheduled";
    static final String NOTIFY_EXAM_BOARD_CANCELED_HANDLER = "ExamBoardEventsNotificationListener.notifyExamBoardCanceled";

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public ExamBoardEventsNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher, EntityManager entityManager) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @ApplicationModuleListener
    public void notifyExamBoardRescheduled(ExamBoardRescheduledEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_EXAM_BOARD_RESCHEDULED_HANDLER, () -> eventPublisher.publishEvent(
                new ExamBoardRescheduledNotification(event.examBoardId(), event.subjectName(), event.oldExamDate(), event.newExamDate(),
                        event.classroom(), personsOf(event.registeredStudentPersonIds()), personsOf(event.tribunalPersonIds()))));
    }

    @ApplicationModuleListener
    public void notifyExamBoardCanceled(ExamBoardCanceledEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_EXAM_BOARD_CANCELED_HANDLER, () -> eventPublisher.publishEvent(
                new ExamBoardCanceledNotification(event.examBoardId(), event.subjectName(), event.examDate(), event.reason(),
                        personsOf(event.registeredStudentPersonIds()), personsOf(event.tribunalPersonIds()))));
    }

    private List<Person> personsOf(List<Long> personIds) {
        return personIds.stream().map(personId -> entityManager.getReference(Person.class, personId)).toList();
    }
}
