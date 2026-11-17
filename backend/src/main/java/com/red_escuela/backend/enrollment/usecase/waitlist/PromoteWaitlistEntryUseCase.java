package com.red_escuela.backend.enrollment.usecase.waitlist;

import com.red_escuela.backend.enrollment.dto.WaitlistKind;

public interface PromoteWaitlistEntryUseCase {
    void promote(WaitlistKind kind, Long enrollmentId);
}
