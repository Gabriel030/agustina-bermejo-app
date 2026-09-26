package com.red_escuela.backend.announcement.exception;

public class AnnouncementNotFoundException extends RuntimeException {

    public AnnouncementNotFoundException(Long id) {
        super("Announcement " + id + " not found");
    }
}
