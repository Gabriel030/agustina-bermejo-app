package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.dto.WaitlistQueueSummaryResponse;

final class WaitlistSummaries {

    private WaitlistSummaries() {
    }

    static WaitlistQueueSummaryResponse of(WaitlistKind kind, WaitlistQueueSnapshot snapshot) {
        return new WaitlistQueueSummaryResponse(kind, snapshot.id(), snapshot.name(), snapshot.termName(), snapshot.capacity(),
                snapshot.occupied(), snapshot.free(), snapshot.waiting(), snapshot.nextExpiry());
    }
}
