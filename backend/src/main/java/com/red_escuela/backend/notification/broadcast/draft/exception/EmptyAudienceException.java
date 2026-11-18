package com.red_escuela.backend.notification.broadcast.draft.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class EmptyAudienceException extends ConflictException {

    public EmptyAudienceException() {
        super("The broadcast has no eligible recipients");
    }
}
