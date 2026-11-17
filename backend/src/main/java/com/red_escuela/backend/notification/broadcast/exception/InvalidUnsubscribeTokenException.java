package com.red_escuela.backend.notification.broadcast.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class InvalidUnsubscribeTokenException extends NotFoundException implements ReportsErrorCode {

    public InvalidUnsubscribeTokenException() {
        super("Unsubscribe token is invalid");
    }
}
