package com.red_escuela.backend.academicstructure.events;

import com.red_escuela.backend.academicstructure.entity.SectionModificationProposal;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record SectionModificationApprovedEvent(String correlationId, Instant occurredAt, Long proposalId, Long sectionId,
                                         String subjectName, String sectionName, Long requestedByPersonId) implements DomainEvent {

    public static SectionModificationApprovedEvent approvedFor(SectionModificationProposal proposal) {
        var section = proposal.getSubjectSection();
        return new SectionModificationApprovedEvent(CorrelationIds.current(), ApplicationTime.instant(), proposal.getId(), section.getId(),
                section.getSubject().getName(), section.getName(),
                proposal.getRequestedBy() == null ? null : proposal.getRequestedBy().getId());
    }
}
