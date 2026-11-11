package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.domain.ClosureSectionKind;
import com.red_escuela.backend.evaluation.entity.ClosureAuditAction;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.entity.CommissionClosure;
import com.red_escuela.backend.evaluation.entity.CommissionClosureAudit;
import com.red_escuela.backend.evaluation.events.ClosureReopenedEvent;
import com.red_escuela.backend.evaluation.exception.ClosureNotAvailableException;
import com.red_escuela.backend.evaluation.exception.ClosureNotReopenableException;
import com.red_escuela.backend.evaluation.exception.InvalidClosureReopenReasonException;
import com.red_escuela.backend.evaluation.repository.CommissionClosureAuditRepository;
import com.red_escuela.backend.evaluation.repository.CommissionClosureRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class CommissionClosureReopener {

    static final int MINIMUM_REASON_LENGTH = 10;

    private final SectionFinder sectionFinder;
    private final CommissionClosureRepository closureRepository;
    private final CommissionClosureAuditRepository auditRepository;
    private final CurrentActorFinder currentActorFinder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public CommissionClosureReopener(SectionFinder sectionFinder, CommissionClosureRepository closureRepository,
            CommissionClosureAuditRepository auditRepository, CurrentActorFinder currentActorFinder,
            ApplicationEventPublisher eventPublisher, Clock clock) {
        this.sectionFinder = sectionFinder;
        this.closureRepository = closureRepository;
        this.auditRepository = auditRepository;
        this.currentActorFinder = currentActorFinder;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public void reopenClosure(ClosureSectionKind sectionKind, Long sectionId, String reason) {
        SectionKind kind = sectionKind.toSectionKind();
        sectionFinder.requireExists(kind, sectionId);
        String trimmedReason = reason == null ? "" : reason.trim();
        if (trimmedReason.length() < MINIMUM_REASON_LENGTH) {
            throw new InvalidClosureReopenReasonException(MINIMUM_REASON_LENGTH);
        }
        Long closureId = closureRepository.findBySectionKindAndSectionId(kind, sectionId)
                .orElseThrow(() -> new ClosureNotAvailableException(sectionId)).getId();
        CommissionClosure closure = closureRepository.findByIdForUpdate(closureId).orElseThrow();
        if (closure.getStatus() != ClosureStatus.CLOSED) {
            throw new ClosureNotReopenableException(closureId);
        }
        closure.reopen();
        closureRepository.save(closure);
        auditRepository.save(CommissionClosureAudit.builder()
                .closureId(closureId)
                .action(ClosureAuditAction.REOPENED)
                .reportVersion(closure.getReportVersion())
                .actorPersonId(currentActorFinder.currentPersonId())
                .reason(trimmedReason)
                .occurredAt(LocalDateTime.now(clock))
                .build());
        eventPublisher.publishEvent(ClosureReopenedEvent.of(closureId, kind.name(), sectionId, closure.getReportNumber(),
                closure.getReportVersion(), trimmedReason));
    }
}
