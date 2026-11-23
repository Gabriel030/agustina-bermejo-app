package com.red_escuela.backend.notification.invitation.usecase;

import com.red_escuela.backend.notification.invitation.specialist.InvitationFailedRetryTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RetryFailedInvitationsService implements RetryFailedInvitationsUseCase {

    private final InvitationFailedRetryTransaction retryTransaction;

    public RetryFailedInvitationsService(InvitationFailedRetryTransaction retryTransaction) {
        this.retryTransaction = retryTransaction;
    }

    @Override
    public int retryFailedInvitations(Long waveId) {
        return retryTransaction.retryFailedInvitations(waveId);
    }
}
