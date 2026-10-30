package com.red_escuela.backend.evaluation.events;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record SectionEvaluationConfigReminderEvent(String correlationId, Instant occurredAt, Long configId, String subjectName, String sectionName,
                                                   Long instructorPersonId, int daysToEnrollmentStart) implements DomainEvent {

    public static SectionEvaluationConfigReminderEvent reminderFor(SectionEvaluationConfig config, Person instructor, int daysToEnrollmentStart) {
        return new SectionEvaluationConfigReminderEvent(CorrelationIds.current(), ApplicationTime.instant(), config.getId(),
                config.getSubjectSection().getSubject().getName(), config.getSubjectSection().getName(), instructor.getId(), daysToEnrollmentStart);
    }
}
