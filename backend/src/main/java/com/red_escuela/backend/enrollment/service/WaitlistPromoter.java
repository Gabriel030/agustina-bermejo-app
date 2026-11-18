package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class WaitlistPromoter {

    private final WaitlistQueues waitlistQueues;
    private final WaitlistCandidateEligibility eligibility;

    public WaitlistPromoter(WaitlistQueues waitlistQueues, WaitlistCandidateEligibility eligibility) {
        this.waitlistQueues = waitlistQueues;
        this.eligibility = eligibility;
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
        return queue.findWaitingCandidates(queueId).stream()
                .filter(candidate -> eligibility.evaluate(candidate).eligible())
                .findFirst()
                .map(candidate -> {
                    queue.promote(candidate.enrollmentId());
                    return true;
                })
                .orElse(false);
    }

    public int promoteWhileSeatsAreFree(WaitlistKind kind, Long queueId) {
        int promoted = 0;
        while (promoteNext(kind, queueId)) {
            promoted++;
        }
        return promoted;
    }
}
