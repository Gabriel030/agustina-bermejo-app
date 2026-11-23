package com.red_escuela.backend.notification.invitation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class InvitationPersonAlreadyInvitedException extends ConflictException {

    public InvitationPersonAlreadyInvitedException() {
        super("A recipient is already pending or queued in another invitation wave");
    }
}
