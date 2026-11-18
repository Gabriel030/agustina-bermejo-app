package com.red_escuela.backend.enrollment.usecase.waitlist;

import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.service.WaitlistEntryActions;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class PromoteWaitlistEntryService implements PromoteWaitlistEntryUseCase {

    private final WaitlistEntryActions waitlistEntryActions;

    public PromoteWaitlistEntryService(WaitlistEntryActions waitlistEntryActions) {
        this.waitlistEntryActions = waitlistEntryActions;
    }

    @Override
    public void promote(WaitlistKind kind, Long enrollmentId) {
        waitlistEntryActions.promote(kind, enrollmentId);
    }
}
