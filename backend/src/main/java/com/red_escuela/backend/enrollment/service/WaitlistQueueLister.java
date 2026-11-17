package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.dto.WaitlistQueueSummaryResponse;
import org.springframework.stereotype.Service;
import com.red_escuela.backend.shared.pagination.PageResponse;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

@Service
@Transactional(readOnly = true)
public class WaitlistQueueLister {

    private final WaitlistQueues waitlistQueues;
    private final Clock clock;

    public WaitlistQueueLister(WaitlistQueues waitlistQueues, Clock clock) {
        this.waitlistQueues = waitlistQueues;
        this.clock = clock;
    }

    public PageResponse<WaitlistQueueSummaryResponse> list(WaitlistKind kind, Long termId, Integer expiringWithinDays, int page, int size) {
        List<WaitlistQueue> selected = kind == null ? waitlistQueues.all() : List.of(waitlistQueues.of(kind));
        Predicate<WaitlistQueueSnapshot> expiring = expiringWithin(expiringWithinDays);
        List<WaitlistQueueSummaryResponse> summaries = selected.stream()
                .flatMap(queue -> queue.findQueues(null, termId).stream()
                        .filter(expiring)
                        .map(snapshot -> WaitlistSummaries.of(queue.kind(), snapshot)))
                .sorted(Comparator.comparingLong(WaitlistQueueSummaryResponse::waiting).reversed()
                        .thenComparing(WaitlistQueueSummaryResponse::name)
                        .thenComparing(WaitlistQueueSummaryResponse::id))
                .toList();
        return PageResponse.of(summaries, page, size);
    }

    private Predicate<WaitlistQueueSnapshot> expiringWithin(Integer days) {
        if (days == null) {
            return snapshot -> true;
        }
        LocalDate limit = LocalDate.now(clock).plusDays(days);
        return snapshot -> snapshot.nextExpiry() != null && !snapshot.nextExpiry().isAfter(limit);
    }
}
