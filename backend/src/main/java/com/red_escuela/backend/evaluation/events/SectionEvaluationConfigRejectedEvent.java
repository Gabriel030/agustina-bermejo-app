package com.red_escuela.backend.evaluation.events;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record SectionEvaluationConfigRejectedEvent(String correlationId, Instant occurredAt, Long configId, String subjectName, String sectionName,
                                                   String rejectionReason, Long instructorPersonId) implements DomainEvent {

    public static SectionEvaluationConfigRejectedEvent rejectedFor(SectionEvaluationConfig config, Long instructorPersonId) {
        return new SectionEvaluationConfigRejectedEvent(CorrelationIds.current(), ApplicationTime.instant(), config.getId(),
                config.getSubjectSection().getSubject().getName(), config.getSubjectSection().getName(), config.getRejectionReason(), instructorPersonId);
    }
}
