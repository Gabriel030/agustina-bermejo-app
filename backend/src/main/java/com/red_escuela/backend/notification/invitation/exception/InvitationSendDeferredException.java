package com.red_escuela.backend.notification.invitation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class InvitationSendDeferredException extends ConflictException {

    public InvitationSendDeferredException(Long recipientId) {
        super("Invitation send for recipient " + recipientId + " was deferred because the mail channel is unavailable");
    }
}
