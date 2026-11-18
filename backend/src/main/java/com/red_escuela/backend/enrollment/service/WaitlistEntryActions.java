package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.events.WaitlistEntryPromotedEvent;
import com.red_escuela.backend.enrollment.events.WaitlistEntryRemovedEvent;
import com.red_escuela.backend.enrollment.exception.WaitlistCandidateNotEligibleException;
import com.red_escuela.backend.enrollment.exception.WaitlistNoFreeSeatException;
import com.red_escuela.backend.enrollment.exception.WaitlistRemovalBlockedByPendingReceiptException;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
@Transactional
public class WaitlistEntryActions {

    private final WaitlistEntryLoader loader;
    private final WaitlistCandidateEligibility eligibility;
    private final AuditTrail auditTrail;
    private final ApplicationEventPublisher eventPublisher;

    public WaitlistEntryActions(WaitlistEntryLoader loader, WaitlistCandidateEligibility eligibility, AuditTrail auditTrail,
                                ApplicationEventPublisher eventPublisher) {
        this.loader = loader;
        this.eligibility = eligibility;
        this.auditTrail = auditTrail;
        this.eventPublisher = eventPublisher;
    }

    public void promote(WaitlistKind kind, Long enrollmentId) {
        WaitlistEntryContext context = loader.loadLocked(kind, enrollmentId);
        Long free = context.snapshot().free();
        if (free != null && free <= 0) {
            throw new WaitlistNoFreeSeatException();
        }
        if (!eligibility.evaluate(context.candidate()).eligible()) {
            throw new WaitlistCandidateNotEligibleException();
        }
        context.queue().promote(enrollmentId);
        auditTrail.record(new AuditRecord(AuditAction.WAITLIST_PROMOTED_BY_STAFF, context.queue().auditEntityType(), enrollmentId,
                "WAITLISTED", "ENROLLED", null));
        if (kind != WaitlistKind.PROGRAM_SECTION) {
            eventPublisher.publishEvent(WaitlistEntryPromotedEvent.of(enrollmentId, context.owner().studentPersonId(), context.snapshot().name()));
        }
    }

    public void prioritize(WaitlistKind kind, Long enrollmentId, String reason) {
        String validReason = WaitlistReason.validated(reason);
        WaitlistEntryContext context = loader.loadLocked(kind, enrollmentId);
        List<WaitlistCandidate> waiting = context.queue().findWaitingCandidates(context.owner().queueId());
        WaitlistCandidate head = waiting.get(0);
        if (head.enrollmentId().equals(enrollmentId) && head.prioritizedAt() != null) {
            return;
        }
        LocalDateTime now = ApplicationTime.now();
        LocalDateTime latest = waiting.stream().map(WaitlistCandidate::prioritizedAt).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        LocalDateTime at = latest != null && !now.isAfter(latest) ? latest.plusNanos(1000) : now;
        context.queue().markPrioritized(enrollmentId, at);
        auditTrail.record(new AuditRecord(AuditAction.WAITLIST_PRIORITIZED_BY_STAFF, context.queue().auditEntityType(), enrollmentId,
                null, "PRIORITIZED", validReason));
    }

    public void remove(WaitlistKind kind, Long enrollmentId, String reason) {
        String validReason = WaitlistReason.validated(reason);
        WaitlistEntryContext context = loader.loadLocked(kind, enrollmentId);
        if (context.queue().hasReceiptBlockingRemoval(enrollmentId)) {
            throw new WaitlistRemovalBlockedByPendingReceiptException();
        }
        context.queue().remove(enrollmentId);
        auditTrail.record(new AuditRecord(AuditAction.WAITLIST_REMOVED_BY_STAFF, context.queue().auditEntityType(), enrollmentId,
                "WAITLISTED", "WITHDRAWN", validReason));
        eventPublisher.publishEvent(WaitlistEntryRemovedEvent.of(enrollmentId, context.owner().studentPersonId(), context.snapshot().name()));
    }
}
