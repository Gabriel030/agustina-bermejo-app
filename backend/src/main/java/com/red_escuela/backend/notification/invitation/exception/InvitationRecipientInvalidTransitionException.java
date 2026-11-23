package com.red_escuela.backend.notification.invitation.exception;

import com.red_escuela.backend.notification.invitation.domain.InvitationRecipientEvent;
import com.red_escuela.backend.notification.invitation.domain.InvitationRecipientStatus;
import com.red_escuela.backend.shared.exception.ConflictException;

public class InvitationRecipientInvalidTransitionException extends ConflictException {

    public InvitationRecipientInvalidTransitionException(InvitationRecipientStatus from, InvitationRecipientEvent event) {
        super("Invitation recipient in status " + from + " does not accept " + event);
    }
}
