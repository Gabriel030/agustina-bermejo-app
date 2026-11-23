package com.red_escuela.backend.notification.invitation.exception;

import com.red_escuela.backend.notification.invitation.domain.InvitationWaveEvent;
import com.red_escuela.backend.notification.invitation.domain.InvitationWaveStatus;
import com.red_escuela.backend.shared.exception.ConflictException;

public class InvitationWaveInvalidTransitionException extends ConflictException {

    public InvitationWaveInvalidTransitionException(InvitationWaveStatus from, InvitationWaveEvent event) {
        super("Invitation wave in status " + from + " does not accept " + event);
    }
}
