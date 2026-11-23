package com.red_escuela.backend.academicrecord.usecase;

import com.red_escuela.backend.academicrecord.service.RevokeCalendarFeedTokenTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RevokeMyCalendarFeedTokenService implements RevokeMyCalendarFeedTokenUseCase {

    private final RevokeCalendarFeedTokenTransaction revocation;

    public RevokeMyCalendarFeedTokenService(RevokeCalendarFeedTokenTransaction revocation) {
        this.revocation = revocation;
    }

    @Override
    public void revoke(Long studentId) {
        revocation.revoke(studentId);
    }
}
