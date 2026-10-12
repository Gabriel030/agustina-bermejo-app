package com.red_escuela.backend.announcement.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class AnnouncementAlreadySentException extends ConflictException {

    public AnnouncementAlreadySentException(Long announcementId) {
        super("El aviso " + announcementId + " ya fue enviado.");
    }
}
