package com.red_escuela.backend.notification.broadcast.draft.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class BroadcastTemplateMismatchException extends BadRequestException {

    public BroadcastTemplateMismatchException() {
        super("Template channel and category must match the broadcast channel and category");
    }
}
