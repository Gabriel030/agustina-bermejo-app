package com.red_escuela.backend.notification.invitation.usecase;

public interface RetryFailedInvitationsUseCase {

    int retryFailedInvitations(Long waveId);
}
