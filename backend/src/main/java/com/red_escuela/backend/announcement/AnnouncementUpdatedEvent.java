package com.red_escuela.backend.announcement;

import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.DomainNotificationEvent;
import com.red_escuela.backend.notification.NotificationRecipient;
import com.red_escuela.backend.notification.PersonRecipient;
import com.red_escuela.backend.notification.entity.NotificationType;

import java.util.List;

public record AnnouncementUpdatedEvent(Announcement announcement, String sectionName, List<Person> students)
        implements DomainNotificationEvent {

    @Override
    public NotificationType type() {
        return NotificationType.ANNOUNCEMENT_UPDATED;
    }

    @Override
    public String message() {
        return "Cambió un aviso de " + sectionName;
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return students.stream().map(person -> (NotificationRecipient) new PersonRecipient(person)).toList();
    }

    @Override
    public String relatedEntityType() {
        return "Announcement";
    }

    @Override
    public Long relatedEntityId() {
        return announcement.getId();
    }
}
