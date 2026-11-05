package com.red_escuela.backend.notification;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;

import java.util.List;

public record AnnouncementPublishedNotification(Long announcementId, String sectionName, List<Person> students) implements DomainNotificationEvent {
    @Override
    public NotificationType type() {
        return NotificationType.ANNOUNCEMENT_PUBLISHED;
    }

    @Override
    public String message() {
        return "Nuevo aviso en " + sectionName;
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
        return announcementId;
    }
}
