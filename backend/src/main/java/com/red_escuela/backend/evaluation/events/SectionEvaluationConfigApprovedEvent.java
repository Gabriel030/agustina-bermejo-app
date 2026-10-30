package com.red_escuela.backend.evaluation.events;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record SectionEvaluationConfigApprovedEvent(String correlationId, Instant occurredAt, Long configId, String subjectName, String sectionName,
                                                   Long instructorPersonId) implements DomainEvent {

    public static SectionEvaluationConfigApprovedEvent approvedFor(SectionEvaluationConfig config, Person instructor) {
        return new SectionEvaluationConfigApprovedEvent(CorrelationIds.current(), ApplicationTime.instant(), config.getId(),
                config.getSubjectSection().getSubject().getName(), config.getSubjectSection().getName(), instructor.getId());
    }
}
