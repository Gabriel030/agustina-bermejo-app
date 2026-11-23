package com.red_escuela.backend.notification.invitation.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class InvitationWaveImportBatchNotFoundException extends NotFoundException implements ReportsErrorCode {

    public InvitationWaveImportBatchNotFoundException(Long importBatchId) {
        super("Import batch " + importBatchId + " not found");
    }
}
