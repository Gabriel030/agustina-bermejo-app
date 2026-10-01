package com.red_escuela.backend.announcement.exception;

public class AnnouncementAlreadySentException extends RuntimeException {

    public AnnouncementAlreadySentException(Long announcementId) {
        super("El aviso " + announcementId + " ya fue enviado.");
    }
}
