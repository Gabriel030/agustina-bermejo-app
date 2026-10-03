package com.red_escuela.backend.announcement.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class CannotChangeAudienceOfSentAnnouncementException extends ConflictException {

    public CannotChangeAudienceOfSentAnnouncementException(Long announcementId) {
        super("El aviso " + announcementId + " ya se envió: sus destinatarios no se pueden cambiar.");
    }
}
