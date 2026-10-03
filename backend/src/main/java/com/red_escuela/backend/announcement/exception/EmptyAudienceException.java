package com.red_escuela.backend.announcement.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class EmptyAudienceException extends BadRequestException {

    public EmptyAudienceException() {
        super("The selected audience resolves to zero students");
    }
}
