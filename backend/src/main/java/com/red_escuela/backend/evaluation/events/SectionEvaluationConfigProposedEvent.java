package com.red_escuela.backend.evaluation.events;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record SectionEvaluationConfigProposedEvent(String correlationId, Instant occurredAt, Long configId, String subjectName, String sectionName)
        implements DomainEvent {

    public static SectionEvaluationConfigProposedEvent proposedFor(SectionEvaluationConfig config) {
        return new SectionEvaluationConfigProposedEvent(CorrelationIds.current(), ApplicationTime.instant(), config.getId(),
                config.getSubjectSection().getSubject().getName(), config.getSubjectSection().getName());
    }
}
