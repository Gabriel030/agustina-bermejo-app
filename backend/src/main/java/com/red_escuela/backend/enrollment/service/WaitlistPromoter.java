package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.dto.WaitlistVerdict;
import com.red_escuela.backend.enrollment.dto.WaitlistVerdictReason;
import com.red_escuela.backend.enrollment.events.WaitlistEntryPausedEvent;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@Transactional
public class WaitlistPromoter {

    private final WaitlistQueues waitlistQueues;
    private final WaitlistCandidateEligibility eligibility;
    private final AuditTrail auditTrail;
    private final ApplicationEventPublisher eventPublisher;

    public WaitlistPromoter(WaitlistQueues waitlistQueues, WaitlistCandidateEligibility eligibility, AuditTrail auditTrail,
                            ApplicationEventPublisher eventPublisher) {
        this.waitlistQueues = waitlistQueues;
        this.eligibility = eligibility;
        this.auditTrail = auditTrail;
        this.eventPublisher = eventPublisher;
    }

    public boolean promoteNext(WaitlistKind kind, Long queueId) {
        WaitlistQueue queue = waitlistQueues.of(kind);
        queue.lock(queueId);
        if (!queue.isOpenForPromotion(queueId)) {
            return false;
        }
        boolean hasFreeSeat = queue.findQueues(queueId, null).stream()
                .findFirst()
                .map(snapshot -> snapshot.free() == null || snapshot.free() > 0)
                .orElse(false);
        if (!hasFreeSeat) {
            return false;
        }
        List<WaitlistCandidate> skippedByScheduleOverlap = new ArrayList<>();
        for (WaitlistCandidate candidate : queue.findWaitingCandidates(queueId)) {
            WaitlistVerdict verdict = eligibility.evaluate(candidate);
            if (verdict.eligible()) {
                queue.promote(candidate.enrollmentId());
                auditTrail.record(new AuditRecord(AuditAction.WAITLIST_PROMOTED_AUTOMATICALLY, queue.auditEntityType(),
                        candidate.enrollmentId(), "WAITLISTED", "ENROLLED", null));
                skippedByScheduleOverlap.forEach(skipped -> announcePausedEntry(queue, queueId, skipped));
                return true;
            }
            if (verdict.reason() == WaitlistVerdictReason.SCHEDULE_OVERLAP) {
                skippedByScheduleOverlap.add(candidate);
            }
        }
        return false;
    }

    private void announcePausedEntry(WaitlistQueue queue, Long queueId, WaitlistCandidate skipped) {
        Optional<WaitlistEntryOwner> owner = queue.findOwner(skipped.enrollmentId());
        Optional<String> conflictingSectionName = eligibility.findConflictingSectionName(skipped);
        if (owner.isEmpty() || conflictingSectionName.isEmpty()) {
            return;
        }
        queue.findQueues(queueId, null).stream().findFirst().ifPresent(snapshot ->
                eventPublisher.publishEvent(WaitlistEntryPausedEvent.of(skipped.enrollmentId(), owner.get().studentPersonId(),
                        snapshot.name(), conflictingSectionName.get())));
    }

    public int promoteWhileSeatsAreFree(WaitlistKind kind, Long queueId) {
        int promoted = 0;
        while (promoteNext(kind, queueId)) {
            promoted++;
        }
        return promoted;
    }
}
