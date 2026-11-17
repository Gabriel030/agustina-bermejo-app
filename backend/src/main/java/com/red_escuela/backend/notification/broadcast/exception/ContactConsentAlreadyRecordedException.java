package com.red_escuela.backend.notification.broadcast.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ContactConsentAlreadyRecordedException extends ConflictException {

    public ContactConsentAlreadyRecordedException(String category) {
        super("Contact consent already recorded for " + category);
    }
}
