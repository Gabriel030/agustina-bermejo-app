package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.exception.WaitlistEntryNotWaitingException;
import com.red_escuela.backend.enrollment.exception.WaitlistQueueNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class WaitlistEntryLoader {

    private final WaitlistQueues waitlistQueues;

    public WaitlistEntryLoader(WaitlistQueues waitlistQueues) {
        this.waitlistQueues = waitlistQueues;
    }

    public WaitlistEntryContext loadLocked(WaitlistKind kind, Long enrollmentId) {
        WaitlistQueue queue = waitlistQueues.of(kind);
        WaitlistEntryOwner owner = queue.findOwner(enrollmentId)
                .orElseThrow(() -> new WaitlistQueueNotFoundException(kind.name(), enrollmentId));
        queue.lock(owner.queueId());
        WaitlistCandidate candidate = queue.findWaitingCandidates(owner.queueId()).stream()
                .filter(waiting -> waiting.enrollmentId().equals(enrollmentId))
                .findFirst()
                .orElseThrow(WaitlistEntryNotWaitingException::new);
        WaitlistQueueSnapshot snapshot = queue.findQueues(owner.queueId(), null).stream().findFirst()
                .orElseThrow(() -> new WaitlistQueueNotFoundException(kind.name(), owner.queueId()));
        return new WaitlistEntryContext(queue, owner, candidate, snapshot);
    }
}
