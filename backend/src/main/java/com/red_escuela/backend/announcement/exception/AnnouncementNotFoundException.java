package com.red_escuela.backend.announcement.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class AnnouncementNotFoundException extends NotFoundException {

    public AnnouncementNotFoundException(Long id) {
        super("Announcement " + id + " not found");
    }
}
