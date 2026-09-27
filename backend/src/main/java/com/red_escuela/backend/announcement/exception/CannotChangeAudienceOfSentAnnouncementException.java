package com.red_escuela.backend.announcement.exception;

public class CannotChangeAudienceOfSentAnnouncementException extends RuntimeException {

    public CannotChangeAudienceOfSentAnnouncementException(Long announcementId) {
        super("El aviso " + announcementId + " ya se envió: sus destinatarios no se pueden cambiar.");
    }
}
