package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.events.StudentReactivatedEvent;
import com.red_escuela.backend.notification.domain.ClosureNotification;
import com.red_escuela.backend.notification.domain.PersonRecipient;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StudentReactivationNotificationListener {
    static final String NOTIFY_STUDENT_REACTIVATED_HANDLER = "StudentReactivationNotificationListener.notifyStudentReactivated";
    static final String STUDENT_ENTITY_TYPE = "Student";

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public StudentReactivationNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher,
            EntityManager entityManager) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @ApplicationModuleListener
    public void notifyStudentReactivated(StudentReactivatedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_STUDENT_REACTIVATED_HANDLER,
                () -> eventPublisher.publishEvent(new ClosureNotification(NotificationType.STUDENT_REACTIVATED,
                        "Tu cuenta fue reactivada: ya podés volver a ingresar. Tu historia académica se conservó.",
                        List.of(new PersonRecipient(entityManager.getReference(Person.class, event.personId()))),
                        STUDENT_ENTITY_TYPE, event.studentId())));
    }
}
