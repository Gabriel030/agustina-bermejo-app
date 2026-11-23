package com.red_escuela.backend.notification.invitation.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvitationRecipientDeliveryRequiredException extends BadRequestException {

    public InvitationRecipientDeliveryRequiredException() {
        super("A delivery id is required to queue an invitation recipient");
    }
}
