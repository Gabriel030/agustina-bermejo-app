package com.red_escuela.backend.notification.invitation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class InvitationWaveHasOpenRecipientsException extends ConflictException {

    public InvitationWaveHasOpenRecipientsException(Long waveId) {
        super("Invitation wave " + waveId + " still has pending or queued recipients");
    }
}
