package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.WaitlistEntryResponse;
import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.dto.WaitlistQueueResponse;
import com.red_escuela.backend.enrollment.exception.WaitlistQueueNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.IntStream;

@Service
@Transactional(readOnly = true)
public class WaitlistQueueReader {

    private final WaitlistQueues waitlistQueues;
    private final WaitlistCandidateEligibility eligibility;

    public WaitlistQueueReader(WaitlistQueues waitlistQueues, WaitlistCandidateEligibility eligibility) {
        this.waitlistQueues = waitlistQueues;
        this.eligibility = eligibility;
    }

    public WaitlistQueueResponse read(WaitlistKind kind, Long queueId) {
        WaitlistQueue queue = waitlistQueues.of(kind);
        WaitlistQueueSnapshot snapshot = queue.findQueues(queueId, null).stream()
                .findFirst()
                .orElseThrow(() -> new WaitlistQueueNotFoundException(kind.name(), queueId));
        List<WaitlistCandidate> candidates = queue.findWaitingCandidates(queueId);
        List<WaitlistEntryResponse> entries = IntStream.range(0, candidates.size())
                .mapToObj(index -> entryOf(index + 1, candidates.get(index), snapshot))
                .toList();
        return WaitlistQueueResponse.of(WaitlistSummaries.of(kind, snapshot), entries);
    }

    private WaitlistEntryResponse entryOf(int position, WaitlistCandidate candidate, WaitlistQueueSnapshot snapshot) {
        return new WaitlistEntryResponse(position, candidate.enrollmentId(),
                new WaitlistEntryResponse.WaitlistStudent(candidate.studentId(), candidate.fullName(), candidate.documentNumber()),
                candidate.joinedAt(), eligibility.evaluate(candidate), snapshot.nextExpiry());
    }
}
