package com.red_escuela.backend.enrollment.usecase.waitlist;

import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.service.WaitlistEntryActions;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class PrioritizeWaitlistEntryService implements PrioritizeWaitlistEntryUseCase {

    private final WaitlistEntryActions waitlistEntryActions;

    public PrioritizeWaitlistEntryService(WaitlistEntryActions waitlistEntryActions) {
        this.waitlistEntryActions = waitlistEntryActions;
    }

    @Override
    public void prioritize(WaitlistKind kind, Long enrollmentId, String reason) {
        waitlistEntryActions.prioritize(kind, enrollmentId, reason);
    }
}
