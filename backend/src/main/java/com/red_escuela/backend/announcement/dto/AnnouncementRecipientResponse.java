package com.red_escuela.backend.announcement.dto;

import com.red_escuela.backend.announcement.entity.AnnouncementRecipient;
import com.red_escuela.backend.identity.entity.Person;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record AnnouncementRecipientResponse(Long studentId, String file, String name,
                                            @Schema(nullable = true) LocalDateTime readAt) {

    public static AnnouncementRecipientResponse from(AnnouncementRecipient recipient) {
        Person person = recipient.getStudent().getPerson();
        return new AnnouncementRecipientResponse(
                recipient.getStudent().getId(),
                person.getFile(),
                person.getName() + " " + person.getLastname(),
                recipient.getReadAt());
    }
}
