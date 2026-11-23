package com.red_escuela.backend.notification.invitation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class InvitationWaveTooManyRecipientsException extends ConflictException {

    public InvitationWaveTooManyRecipientsException(int maxRecipients) {
        super("The invitation wave exceeds the maximum of " + maxRecipients + " recipients");
    }
}
