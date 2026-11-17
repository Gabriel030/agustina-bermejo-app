package com.red_escuela.backend.enrollment.usecase.waitlist;

import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.service.WaitlistEntryActions;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RemoveWaitlistEntryService implements RemoveWaitlistEntryUseCase {

    private final WaitlistEntryActions waitlistEntryActions;

    public RemoveWaitlistEntryService(WaitlistEntryActions waitlistEntryActions) {
        this.waitlistEntryActions = waitlistEntryActions;
    }

    @Override
    public void remove(WaitlistKind kind, Long enrollmentId, String reason) {
        waitlistEntryActions.remove(kind, enrollmentId, reason);
    }
}
