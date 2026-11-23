package com.red_escuela.backend.notification.invitation.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class InvitationWaveNotFoundException extends NotFoundException implements ReportsErrorCode {

    public InvitationWaveNotFoundException(Long id) {
        super("Invitation wave " + id + " not found");
    }
}
