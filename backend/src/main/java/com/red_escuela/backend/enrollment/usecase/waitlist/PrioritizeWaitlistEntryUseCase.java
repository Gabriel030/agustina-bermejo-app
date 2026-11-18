package com.red_escuela.backend.enrollment.usecase.waitlist;

import com.red_escuela.backend.enrollment.dto.WaitlistKind;

public interface PrioritizeWaitlistEntryUseCase {
    void prioritize(WaitlistKind kind, Long enrollmentId, String reason);
}
