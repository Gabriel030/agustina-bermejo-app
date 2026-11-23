package com.red_escuela.backend.notification.invitation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class InvitationWaveWithoutRecipientsException extends ConflictException {

    public InvitationWaveWithoutRecipientsException(Long waveId) {
        super("Invitation wave " + waveId + " has no pending recipients to start");
    }
}
