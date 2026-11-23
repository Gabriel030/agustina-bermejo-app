package com.red_escuela.backend.notification.invitation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class InvitationWaveNotRunningException extends ConflictException {

    public InvitationWaveNotRunningException(Long waveId) {
        super("Invitation wave " + waveId + " is not running");
    }
}
