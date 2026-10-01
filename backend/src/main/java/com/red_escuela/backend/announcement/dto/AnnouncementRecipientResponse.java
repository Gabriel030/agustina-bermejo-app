package com.red_escuela.backend.announcement.dto;

import com.red_escuela.backend.announcement.entity.AnnouncementRecipient;
import com.red_escuela.backend.identity.entity.Person;

public record AnnouncementRecipientResponse(Long studentId, String file, String name) {

    public static AnnouncementRecipientResponse from(AnnouncementRecipient recipient) {
        Person person = recipient.getStudent().getPerson();
        return new AnnouncementRecipientResponse(
                recipient.getStudent().getId(),
                person.getFile(),
                person.getName() + " " + person.getLastname());
    }
}
