package com.red_escuela.backend.notification;

import com.red_escuela.backend.entity.NotificationType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;

import java.util.List;

public record SectionEvaluationConfigReminderEvent(SectionEvaluationConfig config, Person instructor, int daysToEnrollmentStart)
        implements DomainNotificationEvent {
    @Override
    public NotificationType type() {
        return NotificationType.SECTION_EVAL_CONFIG_REMINDER;
    }

    @Override
    public String message() {
        return "Faltan " + daysToEnrollmentStart + " días para que abra la inscripción a materias y la configuración de "
                + config.getSubjectSection().getSubject().getName() + " · " + config.getSubjectSection().getName()
                + " todavía no está aprobada.";
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return List.of(new PersonRecipient(instructor), NotificationTopic.EVALUATION_CONFIG_SUPERVISION);
    }

    @Override
    public String relatedEntityType() {
        return "SectionEvaluationConfig";
    }

    @Override
    public Long relatedEntityId() {
        return config.getId();
    }
}
